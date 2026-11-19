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

    long count(UUID workspaceId, UUID documentId) {
        return jdbc.sql("SELECT count(*) FROM document_chunks WHERE workspace_id = ? AND document_id = ?")
                .params(workspaceId, documentId)
                .query(Long.class)
                .single();
    }
}
