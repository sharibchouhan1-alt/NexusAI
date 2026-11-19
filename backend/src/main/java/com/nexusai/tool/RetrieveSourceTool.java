package com.nexusai.tool;

import com.nexusai.document.DocumentService;
import com.nexusai.document.DocumentService.DocumentSummary;
import com.nexusai.ingestion.ChunkSearch;
import com.nexusai.ingestion.ChunkSearch.StoredChunk;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * {@code retrieve_source()}: reads a passage found by an earlier search, together with its
 * neighbouring passages in the same document, to see the full context of a finding.
 * Only handles from this run resolve, and only within the run's workspace.
 */
@Component
public class RetrieveSourceTool implements AgentTool<RetrieveSourceTool.Arguments> {

    public static final String NAME = "retrieve_source";
    static final int DEFAULT_CONTEXT = 1;

    public record Arguments(@NotBlank @Pattern(regexp = "^P\\d{1,4}$", message = "must be a passage handle like P3") String passage,
                            @Min(0) @Max(2) Integer context) {}

    private final ChunkSearch chunks;
    private final DocumentService documents;

    RetrieveSourceTool(ChunkSearch chunks, DocumentService documents) {
        this.chunks = chunks;
        this.documents = documents;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Reads a passage returned by an earlier search together with its neighbouring passages, to check details in context.";
    }

    @Override
    public String argumentsHint() {
        return "{\"passage\": string handle such as \"P3\", \"context\": integer 0-2 neighbouring passages per side (optional, default 1)}";
    }

    @Override
    public Class<Arguments> argumentType() {
        return Arguments.class;
    }

    @Override
    public ToolResult execute(ToolContext context, Arguments arguments) {
        UUID chunkId = context.ledger().resolve(arguments.passage())
                .orElseThrow(() -> new ToolArgumentException("Unknown passage " + arguments.passage()
                        + ": only passages returned by searches in this research run can be retrieved."));
        int window = arguments.context() == null ? DEFAULT_CONTEXT : arguments.context();
        List<StoredChunk> stored = chunks.withContext(context.workspaceId(), chunkId, window);
        if (stored.isEmpty()) {
            throw new ToolExecutionException("Passage " + arguments.passage()
                    + " is no longer available (its document was deleted or re-ingested).", null);
        }
        Map<UUID, DocumentSummary> titles = documents.summaries(context.workspaceId(),
                stored.stream().map(StoredChunk::documentId).collect(Collectors.toSet()));

        List<ToolResult.HandledPassage> passages = new ArrayList<>();
        for (StoredChunk c : stored) {
            DocumentSummary doc = titles.get(c.documentId());
            if (doc == null) continue; // deleted meanwhile
            Passage p = new Passage(c.documentId(), c.chunkId(), doc.title(), c.pageNumber(), c.chunkIndex(),
                    c.charStart(), c.charEnd(), c.content(), null);
            context.ledger().record(p).ifPresent(h -> passages.add(new ToolResult.HandledPassage(h, p)));
        }
        if (passages.isEmpty()) {
            throw new ToolExecutionException("Passage " + arguments.passage() + " could not be read.", null);
        }
        StoredChunk first = stored.getFirst();
        StoredChunk last = stored.getLast();
        String title = titles.containsKey(first.documentId()) ? titles.get(first.documentId()).title() : "document";
        String summary = "Read " + arguments.passage() + " with " + (passages.size() - 1) + " neighbouring passage"
                + (passages.size() == 2 ? "" : "s") + " from \"" + title + "\" (chunks " + (first.chunkIndex() + 1)
                + "–" + (last.chunkIndex() + 1) + "): " + String.join(", ", passages.stream().map(ToolResult.HandledPassage::handle).toList());
        return new ToolResult(summary, passages);
    }
}
