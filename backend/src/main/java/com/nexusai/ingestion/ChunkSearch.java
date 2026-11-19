package com.nexusai.ingestion;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Published query interface over the chunks and embeddings this module owns. The Search
 * module retrieves through it rather than reading these tables itself. Always
 * workspace-scoped; only embeddings of the given model are considered.
 */
@Component
public class ChunkSearch {

    /** A candidate chunk with provenance; {@code distance} is cosine distance (0 = identical). */
    public record ChunkMatch(UUID chunkId, UUID documentId, int chunkIndex, Integer pageNumber,
                             int charStart, int charEnd, String content, double distance) {}

    private final ChunkRepository chunks;

    ChunkSearch(ChunkRepository chunks) {
        this.chunks = chunks;
    }

    public List<ChunkMatch> nearest(UUID workspaceId, String model, float[] queryVector, int limit) {
        return chunks.nearest(workspaceId, model, queryVector, limit);
    }

    /** A stored chunk with provenance (no similarity: it was read, not searched). */
    public record StoredChunk(UUID chunkId, UUID documentId, int chunkIndex, Integer pageNumber,
                              int charStart, int charEnd, String content) {}

    /**
     * The chunk and up to {@code window} neighbouring chunks on each side from the same
     * document, in order. Empty if the chunk doesn't exist in this workspace.
     */
    public List<StoredChunk> withContext(UUID workspaceId, UUID chunkId, int window) {
        return chunks.withContext(workspaceId, chunkId, Math.max(0, Math.min(window, 3)));
    }
}
