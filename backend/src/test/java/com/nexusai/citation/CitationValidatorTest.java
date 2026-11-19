package com.nexusai.citation;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexusai.citation.ModelAnswer.Claim;
import com.nexusai.citation.ValidatedAnswer.Outcome;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CitationValidatorTest {

    private static final UUID DOC_A = UUID.randomUUID();
    private static final UUID DOC_B = UUID.randomUUID();
    private static final CitableSource S1 = new CitableSource(1, DOC_A, UUID.randomUUID(), "Biology", 3, 0, 0, 120,
            "Photosynthesis converts light energy into chemical energy.\nChlorophyll absorbs red and blue light.", 0.71);
    private static final CitableSource S2 = new CitableSource(2, DOC_B, UUID.randomUUID(), "Astronomy", null, 4, 200, 300,
            "Stars fuse hydrogen into helium in their cores.", 0.64);
    private static final List<CitableSource> SOURCES = List.of(S1, S2);

    private final CitationValidator validator = new CitationValidator(12);

    @Test
    void verifiedCitationsAreKeptWithFullProvenance() {
        ValidatedAnswer result = validator.validate(new ModelAnswer(true,
                "Plants convert light into chemical energy [1]. Stars fuse hydrogen [2].",
                List.of(new Claim(1, "converts light energy into chemical energy"),
                        new Claim(2, "Stars fuse hydrogen into helium"))), SOURCES);

        assertThat(result.outcome()).isEqualTo(Outcome.GROUNDED);
        assertThat(result.rejected()).isZero();
        assertThat(result.text()).isEqualTo("Plants convert light into chemical energy [1]. Stars fuse hydrogen [2].");
        assertThat(result.citations()).hasSize(2);
        CitationDraft first = result.citations().getFirst();
        assertThat(first.ordinal()).isEqualTo(1);
        assertThat(first.documentId()).isEqualTo(DOC_A);
        assertThat(first.chunkId()).isEqualTo(S1.chunkId());
        assertThat(first.pageNumber()).isEqualTo(3);
        assertThat(first.passage()).isEqualTo(S1.content());
        assertThat(first.quote()).isEqualTo("converts light energy into chemical energy");
    }

    @Test
    void fabricatedSourceNumbersAreRejectedAndTheirMarkersRemoved() {
        ValidatedAnswer result = validator.validate(new ModelAnswer(true,
                "Plants make energy [1]. The moon is cheese [7].",
                List.of(new Claim(1, "Chlorophyll absorbs red and blue light"),
                        new Claim(7, "The moon is made of cheese entirely"))), SOURCES);

        assertThat(result.outcome()).isEqualTo(Outcome.GROUNDED);
        assertThat(result.rejected()).isEqualTo(1);
        assertThat(result.text()).isEqualTo("Plants make energy [1]. The moon is cheese.");
        assertThat(result.citations()).extracting(CitationDraft::documentId).containsExactly(DOC_A);
    }

    @Test
    void quotesThatAreNotInThePassageAreRejected() {
        ValidatedAnswer result = validator.validate(new ModelAnswer(true, "Stars burn coal [2].",
                List.of(new Claim(2, "Stars burn coal in their cores"))), SOURCES);
        assertThat(result.outcome()).isEqualTo(Outcome.UNVERIFIABLE);
        assertThat(result.citations()).isEmpty();
        assertThat(result.text()).isNull();
    }

    @Test
    void quoteMatchingIgnoresCaseWhitespaceQuoteStylesAndAcceptsElision() {
        assertThat(validator.validate(new ModelAnswer(true, "x [1]",
                List.of(new Claim(1, "“PHOTOSYNTHESIS converts   light energy”"))), SOURCES).grounded()).isTrue();
        assertThat(validator.validate(new ModelAnswer(true, "x [1]",
                List.of(new Claim(1, "Photosynthesis converts ... chemical energy"))), SOURCES).grounded()).isTrue();
        // Elided segments must still appear in order.
        assertThat(validator.validate(new ModelAnswer(true, "x [1]",
                List.of(new Claim(1, "chemical energy ... Photosynthesis converts"))), SOURCES).grounded()).isFalse();
    }

    @Test
    void wordsBrokenAcrossPdfLinesStillVerifyButDifferentWordsDoNot() {
        CitableSource pdf = new CitableSource(1, DOC_A, UUID.randomUUID(), "Astro", 1, 0, 0, 90,
                "as a supernova, scattering heavy elements such as iron and gold across the galax\ny.", 0.6);
        assertThat(validator.validate(new ModelAnswer(true, "x [1]",
                List.of(new Claim(1, "scattering heavy elements such as iron and gold across the galaxy"))), List.of(pdf))
                .grounded()).isTrue();
        assertThat(validator.validate(new ModelAnswer(true, "x [1]",
                List.of(new Claim(1, "scattering heavy elements such as silver and gold across the galaxy"))), List.of(pdf))
                .grounded()).isFalse();
    }

    @Test
    void tooShortQuotesCannotVerifyAnything() {
        ValidatedAnswer result = validator.validate(new ModelAnswer(true, "Yes [1].",
                List.of(new Claim(1, "light"))), SOURCES);
        assertThat(result.outcome()).isEqualTo(Outcome.UNVERIFIABLE);
    }

    @Test
    void markersAreRenumberedByFirstAppearanceAndGroupsSplit() {
        ValidatedAnswer result = validator.validate(new ModelAnswer(true,
                "Stars fuse hydrogen [Source 2], and plants use light [1, 2].",
                List.of(new Claim(1, "Chlorophyll absorbs red and blue light"),
                        new Claim(2, "fuse hydrogen into helium"))), SOURCES);
        assertThat(result.text()).isEqualTo("Stars fuse hydrogen [1], and plants use light [2][1].");
        assertThat(result.citations()).extracting(CitationDraft::documentTitle).containsExactly("Astronomy", "Biology");
    }

    @Test
    void verifiedCitationsWithoutMarkersStillCount() {
        ValidatedAnswer result = validator.validate(new ModelAnswer(true, "Stars fuse hydrogen.",
                List.of(new Claim(2, "Stars fuse hydrogen into helium"))), SOURCES);
        assertThat(result.grounded()).isTrue();
        assertThat(result.citations()).singleElement().extracting(CitationDraft::ordinal).isEqualTo(1);
    }

    @Test
    void duplicateClaimsForOneSourceBecomeOneCitation() {
        ValidatedAnswer result = validator.validate(new ModelAnswer(true, "a [1] b [1]",
                List.of(new Claim(1, "Chlorophyll absorbs red and blue light"),
                        new Claim(1, "converts light energy into chemical energy"))), SOURCES);
        assertThat(result.citations()).hasSize(1);
        assertThat(result.citations().getFirst().quote()).isEqualTo("Chlorophyll absorbs red and blue light");
    }

    @Test
    void modelSayingTheEvidenceIsInsufficientAbstains() {
        ValidatedAnswer result = validator.validate(new ModelAnswer(false, "Not covered.", List.of()), SOURCES);
        assertThat(result.outcome()).isEqualTo(Outcome.INSUFFICIENT_EVIDENCE);
        assertThat(result.text()).isNull();
    }

    @Test
    void emptyAnswerOrNoCitationsIsUnverifiable() {
        assertThat(validator.validate(new ModelAnswer(true, "  ", List.of(new Claim(1, "Chlorophyll absorbs red and blue light"))), SOURCES)
                .outcome()).isEqualTo(Outcome.UNVERIFIABLE);
        assertThat(validator.validate(new ModelAnswer(true, "Confident claim with no evidence.", List.of()), SOURCES)
                .outcome()).isEqualTo(Outcome.UNVERIFIABLE);
    }
}
