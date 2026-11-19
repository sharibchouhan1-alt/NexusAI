package com.nexusai.tool;

import com.nexusai.platform.ai.EmbeddingUnavailableException;
import com.nexusai.search.SearchResponse;
import com.nexusai.search.SearchService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** {@code search_knowledge()}: semantic search over the run's workspace via the Search module. */
@Component
public class SearchKnowledgeTool implements AgentTool<SearchKnowledgeTool.Arguments> {

    public static final String NAME = "search_knowledge";
    static final int DEFAULT_LIMIT = 5;

    public record Arguments(@NotBlank @Size(max = 300) String query, @Min(1) @Max(8) Integer limit) {}

    private final SearchService search;
    private final double minScore;

    /** @param minScore the same relevance threshold chat uses: weaker passages are not evidence */
    SearchKnowledgeTool(SearchService search, @Value("${nexusai.rag.min-score}") double minScore) {
        this.search = search;
        this.minScore = minScore;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Semantic search over this workspace's documents. Returns the most relevant passages, each with a handle (P1, P2, ...).";
    }

    @Override
    public String argumentsHint() {
        return "{\"query\": string (a focused search phrase), \"limit\": integer 1-8 (optional, default 5)}";
    }

    @Override
    public Class<Arguments> argumentType() {
        return Arguments.class;
    }

    @Override
    public ToolResult execute(ToolContext context, Arguments arguments) {
        int limit = arguments.limit() == null ? DEFAULT_LIMIT : arguments.limit();
        List<SearchResponse.Result> results;
        try {
            results = search.retrieve(context.workspaceId(), arguments.query().strip(), limit);
        } catch (EmbeddingUnavailableException e) {
            throw new ToolExecutionException("Search is unavailable: the embedding service (Ollama) could not be reached.", e);
        }
        List<ToolResult.HandledPassage> passages = new ArrayList<>();
        boolean limitReached = false;
        int irrelevant = 0;
        for (SearchResponse.Result r : results) {
            if (r.score() < minScore) {
                irrelevant++;
                continue;
            }
            Passage p = new Passage(r.documentId(), r.chunkId(), r.documentTitle(), r.pageNumber(), r.chunkIndex(),
                    r.charStart(), r.charEnd(), r.content(), r.score());
            var handle = context.ledger().record(p);
            if (handle.isPresent()) {
                passages.add(new ToolResult.HandledPassage(handle.get(), p));
            } else {
                limitReached = true;
            }
        }
        String notes = (irrelevant > 0 ? "; " + irrelevant + " below the relevance threshold omitted" : "")
                + (limitReached ? "; evidence limit reached" : "");
        String summary = passages.isEmpty()
                ? "No relevant passages found for \"" + arguments.query().strip() + "\"" + notes
                : "Found " + passages.size() + " passage" + (passages.size() == 1 ? "" : "s") + ": "
                        + String.join(", ", passages.stream().map(SearchKnowledgeTool::label).toList()) + notes;
        return new ToolResult(summary, passages);
    }

    static String label(ToolResult.HandledPassage hp) {
        Passage p = hp.passage();
        return hp.handle() + " " + p.documentTitle() + (p.pageNumber() != null ? " p." + p.pageNumber() : "")
                + (p.score() != null ? String.format(Locale.ROOT, " (%.2f)", p.score()) : "");
    }
}
