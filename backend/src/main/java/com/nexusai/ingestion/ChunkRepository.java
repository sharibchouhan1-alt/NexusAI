package com.nexusai.ingestion;

import com.nexusai.ingestion.TextChunker.TextChunk;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence of {@code document_chunks}, owned by the ingestion module. Plain JDBC: chunks
 * are written in bulk and read in pages; every query is scoped by workspace and document.
 */
@Repository
class ChunkRepository {

    private static final int BATCH_SIZE = 500;

    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;

    ChunkRepository(JdbcClient jdbc, JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Replaces all chunks of a document in one transaction (re-ingestion is idempotent). */
    @Transactional
    void replace(UUID workspaceId, UUID documentId, List<TextChunk> chunks) {
        jdbc.sql("DELETE FROM document_chunks WHERE workspace_id = ? AND document_id = ?")
                .params(workspaceId, documentId)
                .update();
        jdbcTemplate.batchUpdate("""
                        INSERT INTO document_chunks
                            (id, document_id, workspace_id, chunk_index, page_number, char_start, char_end, content)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                chunks, BATCH_SIZE, (ps, chunk) -> {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, documentId);
                    ps.setObject(3, workspaceId);
                    ps.setInt(4, chunk.index());
                    ps.setObject(5, chunk.pageNumber());
                    ps.setInt(6, chunk.charStart());
                    ps.setInt(7, chunk.charEnd());
                    ps.setString(8, chunk.content());
                });
    }

    List<ChunkResponse> page(UUID workspaceId, UUID documentId, int page, int size) {
        return jdbc.sql("""
                        SELECT id, chunk_index, page_number, char_start, char_end, content
                          FROM document_chunks
                         WHERE workspace_id = :workspaceId AND document_id = :documentId
                         ORDER BY chunk_index
                         LIMIT :limit OFFSET :offset
                        """)
                .param("workspaceId", workspaceId)
                .param("documentId", documentId)
                .param("limit", size)
                .param("offset", (long) page * size)
                .query((rs, row) -> new ChunkResponse(
                        rs.getObject("id", UUID.class),
                        rs.getInt("chunk_index"),
                        rs.getObject("page_number", Integer.class),
                        rs.getInt("char_start"),
                        rs.getInt("char_end"),
                        rs.getString("content")))
                .list();
    }

    // --- embeddings (chunk_embeddings, also owned by the ingestion module) ---

    record ChunkText(UUID id, String content) {}

    /** Next chunks of a document without an embedding for {@code model}, in order. */
    List<ChunkText> findUnembedded(UUID workspaceId, UUID documentId, String model, int limit) {
        return jdbc.sql("""
                        SELECT c.id, c.content
                          FROM document_chunks c
                         WHERE c.workspace_id = :workspaceId AND c.document_id = :documentId
                           AND NOT EXISTS (SELECT 1 FROM chunk_embeddings e
                                            WHERE e.chunk_id = c.id AND e.model = :model)
                         ORDER BY c.chunk_index
                         LIMIT :limit
                        """)
                .param("workspaceId", workspaceId)
                .param("documentId", documentId)
                .param("model", model)
                .param("limit", limit)
                .query((rs, row) -> new ChunkText(rs.getObject("id", UUID.class), rs.getString("content")))
                .list();
    }

    /** Stores one batch (its own short transaction, so progress survives interruptions). */
    @Transactional
    int saveEmbeddings(UUID workspaceId, UUID documentId, String model, List<ChunkText> chunks, List<float[]> vectors) {
        int[][] counts = jdbcTemplate.batchUpdate("""
                        INSERT INTO chunk_embeddings (chunk_id, document_id, workspace_id, model, embedding)
                        VALUES (?, ?, ?, ?, CAST(? AS vector))
                        ON CONFLICT (chunk_id, model) DO NOTHING
                        """,
                java.util.stream.IntStream.range(0, chunks.size()).boxed().toList(), BATCH_SIZE, (ps, i) -> {
                    ps.setObject(1, chunks.get(i).id());
                    ps.setObject(2, documentId);
                    ps.setObject(3, workspaceId);
                    ps.setString(4, model);
                    ps.setString(5, toVectorLiteral(vectors.get(i)));
                });
        int saved = 0;
        for (int[] batch : counts) {
            for (int c : batch) saved += Math.max(c, 0);
        }
        return saved;
    }

    long countEmbeddings(UUID workspaceId, UUID documentId, String model) {
        return jdbc.sql("SELECT count(*) FROM chunk_embeddings WHERE workspace_id = ? AND document_id = ? AND model = ?")
                .params(workspaceId, documentId, model)
                .query(Long.class)
                .single();
    }

    record DocumentRef(UUID workspaceId, UUID documentId) {}

    /** System-level: documents that have chunks without an embedding for {@code model}. */
    List<DocumentRef> documentsMissingEmbeddings(String model) {
        return jdbc.sql("""
                        SELECT DISTINCT c.workspace_id, c.document_id
                          FROM document_chunks c
                         WHERE NOT EXISTS (SELECT 1 FROM chunk_embeddings e
                                            WHERE e.chunk_id = c.id AND e.model = ?)
                        """)
                .params(model)
                .query((rs, row) -> new DocumentRef(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)))
                .list();
    }

    /**
     * Nearest chunks to {@code query} by cosine distance, within one workspace and model.
     * pgvector's iterative HNSW scan keeps returning candidates until enough rows survive
     * the workspace filter, so a small workspace in a large corpus still gets full results.
     */
    @Transactional(readOnly = true)
    List<ChunkSearch.ChunkMatch> nearest(UUID workspaceId, String model, float[] query, int limit) {
        jdbc.sql("SET LOCAL hnsw.iterative_scan = 'relaxed_order'").update();
        String vector = toVectorLiteral(query);
        List<ChunkSearch.ChunkMatch> matches = jdbc.sql("""
                        SELECT c.id, c.document_id, c.chunk_index, c.page_number, c.char_start, c.char_end, c.content,
                               e.embedding <=> CAST(:query AS vector) AS distance
                          FROM chunk_embeddings e
                          JOIN document_chunks c ON c.id = e.chunk_id
                         WHERE e.workspace_id = :workspaceId AND e.model = :model
                         ORDER BY e.embedding <=> CAST(:query AS vector)
                         LIMIT :limit
                        """)
                .param("query", vector)
                .param("workspaceId", workspaceId)
                .param("model", model)
                .param("limit", limit)
                .query((rs, row) -> new ChunkSearch.ChunkMatch(
                        rs.getObject("id", UUID.class),
                        rs.getObject("document_id", UUID.class),
                        rs.getInt("chunk_index"),
                        rs.getObject("page_number", Integer.class),
                        rs.getInt("char_start"),
                        rs.getInt("char_end"),
                        rs.getString("content"),
                        rs.getDouble("distance")))
                .list();
        // relaxed_order may return slightly out of order; restore exact distance order.
        return matches.stream().sorted(java.util.Comparator.comparingDouble(ChunkSearch.ChunkMatch::distance)).toList();
    }

    static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 12).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            if (!Float.isFinite(vector[i])) {
                throw new IllegalArgumentException("Embedding contains a non-finite value");
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }

    long count(UUID workspaceId, UUID documentId) {
        return jdbc.sql("SELECT count(*) FROM document_chunks WHERE workspace_id = ? AND document_id = ?")
                .params(workspaceId, documentId)
                .query(Long.class)
                .single();
    }
}
