package com.nexusai.rag;

import com.nexusai.citation.CitableSource;
import com.nexusai.citation.CitationService;
import com.nexusai.citation.ModelAnswer;
import com.nexusai.citation.ValidatedAnswer;
import com.nexusai.platform.ai.EmbeddingUnavailableException;
import com.nexusai.platform.ai.GenerationService;
import com.nexusai.platform.ai.GenerationUnavailableException;
import com.nexusai.search.SearchResponse;
import com.nexusai.search.SearchService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The grounded-answer flow: retrieve (Search) → assemble context → generate (local model)
 * → verify citations (Citation). Never throws for expected failures; every outcome comes
 * back as a {@link RagAnswer}. Holds no database transaction: model calls are slow.
 *
 * <p>Abstains, without showing any model text, when no passage is relevant enough, when the
 * model says the sources are insufficient, or when none of its citations can be verified.
 */
@Service
@EnableConfigurationProperties(RagProperties.class)
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    static final String NO_RELEVANT_PASSAGES = "No passage in this workspace was relevant enough to the question.";
    static final String MODEL_SAID_INSUFFICIENT = "The retrieved passages don't contain the answer.";
    static final String UNVERIFIABLE = "The model's answer could not be verified against the retrieved passages, so it is not shown.";
    static final String RETRIEVAL_UNAVAILABLE =
            "Couldn't search your documents: the embedding service (Ollama) is unavailable. Check that Ollama is running, then retry.";
    static final String MALFORMED_OUTPUT = "The local model returned an answer in an unexpected format. Retry the question.";

    private final SearchService search;
    private final GenerationService generation;
    private final CitationService citations;
    private final RagProperties properties;
    private final JsonMapper json = JsonMapper.builder().build();

    RagService(SearchService search, GenerationService generation, CitationService citations, RagProperties properties) {
        this.search = search;
        this.generation = generation;
        this.citations = citations;
        this.properties = properties;
    }

    public RagAnswer answer(UUID workspaceId, String question, List<String> earlierQuestions) {
        long started = System.nanoTime();
        String model = generation.modelId();

        // 1. Retrieval (workspace-scoped by Search).
        List<SearchResponse.Result> retrieved;
        try {
            retrieved = search.retrieve(workspaceId, question, properties.topK());
        } catch (EmbeddingUnavailableException e) {
            return failed(RETRIEVAL_UNAVAILABLE, model, 0, started);
        }

        // 2. Context assembly: relevance threshold + character budget.
        List<CitableSource> sources = select(retrieved);
        if (sources.isEmpty()) {
            return abstained(NO_RELEVANT_PASSAGES, model, 0, started);
        }

        // 3. Generation, constrained to the answer schema.
        String raw;
        try {
            raw = generation.generateJson(PromptBuilder.SYSTEM_PROMPT,
                    PromptBuilder.userPrompt(question, sources, recent(earlierQuestions)),
                    PromptBuilder.ANSWER_SCHEMA);
        } catch (GenerationUnavailableException e) {
            return failed(e.getMessage(), model, sources.size(), started);
        }

        // 4. Parse (untrusted) and verify against the exact passages given.
        ModelAnswer draft;
        try {
            draft = parse(raw);
        } catch (RuntimeException e) {
            log.warn("Unparseable model output ({} chars): {}", raw.length(), e.getMessage());
            return failed(MALFORMED_OUTPUT, model, sources.size(), started);
        }
        ValidatedAnswer validated = citations.validate(draft, sources);
        if (validated.rejected() > 0) {
            log.info("Rejected {} unverifiable citation(s) from the model", validated.rejected());
        }
        return switch (validated.outcome()) {
            case GROUNDED -> new RagAnswer(RagAnswer.Outcome.ANSWERED, validated.text(), validated.citations(), null,
                    model, sources.size(), elapsed(started));
            case INSUFFICIENT_EVIDENCE -> abstained(MODEL_SAID_INSUFFICIENT, model, sources.size(), started);
            case UNVERIFIABLE -> abstained(UNVERIFIABLE, model, sources.size(), started);
        };
    }

    private List<CitableSource> select(List<SearchResponse.Result> retrieved) {
        List<CitableSource> sources = new ArrayList<>();
        int budget = properties.maxContextChars();
        for (SearchResponse.Result r : retrieved) {
            if (r.score() < properties.minScore()) {
                continue;
            }
            if (r.content().length() > budget) {
                break;
            }
            budget -= r.content().length();
            sources.add(new CitableSource(sources.size() + 1, r.documentId(), r.chunkId(), r.documentTitle(),
                    r.pageNumber(), r.chunkIndex(), r.charStart(), r.charEnd(), r.content(), r.score()));
        }
        return sources;
    }

    private List<String> recent(List<String> earlierQuestions) {
        int n = properties.historyQuestions();
        return earlierQuestions.size() <= n ? earlierQuestions : earlierQuestions.subList(earlierQuestions.size() - n, earlierQuestions.size());
    }

    /** Parses the model's JSON; tolerates prose or code fences around the object. */
    ModelAnswer parse(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("no JSON object");
        }
        JsonNode node = json.readTree(raw.substring(start, end + 1));
        if (!node.isObject() || !node.has("answerable")) {
            throw new IllegalArgumentException("missing 'answerable'");
        }
        List<ModelAnswer.Claim> claims = new ArrayList<>();
        JsonNode list = node.path("citations");
        if (list.isArray()) {
            for (JsonNode c : list) {
                JsonNode source = c.path("source");
                if (source.canConvertToInt() || source.isTextual()) {
                    int number = source.isTextual() ? parseSourceNumber(source.asString()) : source.asInt();
                    claims.add(new ModelAnswer.Claim(number, c.path("quote").asString("")));
                }
            }
        }
        return new ModelAnswer(node.path("answerable").asBoolean(false), node.path("answer").asString(""), claims);
    }

    private static int parseSourceNumber(String text) {
        String digits = text.replaceAll("\\D", "");
        return digits.isEmpty() ? -1 : Integer.parseInt(digits.length() > 6 ? digits.substring(0, 6) : digits);
    }

    private static RagAnswer abstained(String detail, String model, int retrieved, long started) {
        return new RagAnswer(RagAnswer.Outcome.ABSTAINED, RagAnswer.ABSTENTION_TEXT, List.of(), detail, model,
                retrieved, elapsed(started));
    }

    private static RagAnswer failed(String detail, String model, int retrieved, long started) {
        return new RagAnswer(RagAnswer.Outcome.FAILED, null, List.of(), detail, model, retrieved, elapsed(started));
    }

    private static long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
