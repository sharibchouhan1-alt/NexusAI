package com.nexusai.citation;

import java.util.List;

/**
 * The result of checking a model answer against the passages it was given.
 *
 * @param text       answer with markers renumbered to verified citations only (GROUNDED only)
 * @param rejected   claimed citations that failed verification (fabricated source or quote)
 */
public record ValidatedAnswer(Outcome outcome, String text, List<CitationDraft> citations, int rejected) {

    public enum Outcome {
        /** At least one citation verified; the answer can be shown. */
        GROUNDED,
        /** The model itself said the sources don't answer the question. */
        INSUFFICIENT_EVIDENCE,
        /** The model answered but none of its evidence could be verified: not shown. */
        UNVERIFIABLE
    }

    public boolean grounded() {
        return outcome == Outcome.GROUNDED;
    }
}
