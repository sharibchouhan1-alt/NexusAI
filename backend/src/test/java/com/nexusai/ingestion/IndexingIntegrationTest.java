package com.nexusai.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nexusai.ApiTestSupport;
import com.nexusai.TestEmbeddingModel;
import com.nexusai.TestFiles;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class IndexingIntegrationTest extends ApiTestSupport {

    private static final String MODEL = "ollama:nomic-embed-text";

    @Autowired
    private TestEmbeddingModel embeddingModel;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private IngestionDispatcher dispatcher;

    @Test
    void everyChunkGetsAModelTaggedEmbeddingOfTheRightSize() throws Exception {
        String ws = createWorkspace("Embed");
        String doc = uploadText(ws, "notes.txt", TestFiles.prose(10));

        String body = awaitIndex(ws, doc, "INDEXED");
        int chunks = JsonPath.read(body, "$.chunkCount");
        assertThat(chunks).isGreaterThan(1);
        assertThat((Object) JsonPath.read(body, "$.indexDetail")).isNull();
        assertThat(embeddingRows(doc)).isEqualTo(chunks);
        assertThat(jdbc.queryForObject("SELECT DISTINCT model FROM chunk_embeddings WHERE document_id = ?::uuid",
                String.class, doc)).isEqualTo(MODEL);
        assertThat(jdbc.queryForObject("SELECT DISTINCT vector_dims(embedding) FROM chunk_embeddings WHERE document_id = ?::uuid",
                Integer.class, doc)).isEqualTo(768);
        assertThat(jdbc.queryForObject("SELECT DISTINCT workspace_id::text FROM chunk_embeddings WHERE document_id = ?::uuid",
                String.class, doc)).isEqualTo(ws);
    }

    @Test
    void ollamaDowntimeKeepsTheDocumentReadyAndIndexingResumesAutomatically() throws Exception {
        String ws = createWorkspace("Downtime");
        String doc;
        embeddingModel.setAvailable(false);
        try {
            doc = uploadText(ws, "notes.txt", TestFiles.prose(4));
            // Upload and extraction are unaffected; only search readiness waits.
            String body = awaitIngestion(ws, doc);
            assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("READY");
            body = awaitIndex(ws, doc, "PENDING");
            assertThat((String) JsonPath.read(body, "$.indexDetail")).contains("retries automatically");
            mvc.perform(get("/api/workspaces/{ws}/documents/{id}/chunks", ws, doc))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalItems").value(org.hamcrest.Matchers.greaterThan(0)));
            Thread.sleep(800); // a few scheduler ticks while still down: nothing is lost or failed
            mvc.perform(get("/api/workspaces/{ws}/documents/{id}", ws, doc))
                    .andExpect(jsonPath("$.indexStatus").value("PENDING"));
            assertThat(embeddingRows(doc)).isZero();
        } finally {
            embeddingModel.setAvailable(true);
        }
        // The periodic retry (300ms in tests) picks it up once the service is back.
        String body = awaitIndex(ws, doc, "INDEXED");
        assertThat((Object) JsonPath.read(body, "$.indexDetail")).isNull();
        assertThat(embeddingRows(doc)).isEqualTo((Integer) JsonPath.read(body, "$.chunkCount"));
    }

    @Test
    void wrongEmbeddingSizeFailsIndexingAndManualRetryRecovers() throws Exception {
        String ws = createWorkspace("Contract");
        embeddingModel.setDimensions(384);
        String doc;
        try {
            doc = uploadText(ws, "notes.txt", "A short note about vectors.");
            String body = awaitIndex(ws, doc, "FAILED");
            assertThat((String) JsonPath.read(body, "$.indexDetail")).contains("384").contains("768");
        } finally {
            embeddingModel.setDimensions(TestEmbeddingModel.DIMENSIONS);
        }
        mvc.perform(post("/api/workspaces/{ws}/documents/{id}/embeddings", ws, doc))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.indexStatus").value("PENDING"));
        awaitIndex(ws, doc, "INDEXED");
        assertThat(embeddingRows(doc)).isEqualTo(1);
    }

    @Test
    void reindexRequiresAReadyDocumentAndIsWorkspaceScoped() throws Exception {
        String ws = createWorkspace("Reindex");
        String other = createWorkspace("Other");
        String metadataOnly = createDocument(ws, "No file");
        mvc.perform(post("/api/workspaces/{ws}/documents/{id}/embeddings", ws, metadataOnly))
                .andExpect(status().isConflict());

        String doc = uploadText(ws, "notes.txt", "Indexed text.");
        awaitIndex(ws, doc, "INDEXED");
        mvc.perform(post("/api/workspaces/{ws}/documents/{id}/embeddings", other, doc))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/workspaces/{ws}/documents/{id}/embeddings", ws, UUID.randomUUID()))
                .andExpect(status().isNotFound());

        // Re-indexing an indexed document keeps existing embeddings (nothing is re-embedded).
        String createdBefore = embeddingCreatedAt(doc);
        mvc.perform(post("/api/workspaces/{ws}/documents/{id}/embeddings", ws, doc)).andExpect(status().isAccepted());
        awaitIndex(ws, doc, "INDEXED");
        assertThat(embeddingRows(doc)).isEqualTo(1);
        assertThat(embeddingCreatedAt(doc)).isEqualTo(createdBefore);
    }

    @Test
    void reingestionReplacesEmbeddingsAndDeletionRemovesThem() throws Exception {
        String ws = createWorkspace("Lifecycle");
        String doc = uploadText(ws, "notes.txt", TestFiles.prose(3));
        awaitIndex(ws, doc, "INDEXED");
        int before = embeddingRows(doc);

        mvc.perform(post("/api/workspaces/{ws}/documents/{id}/ingestion", ws, doc))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.indexStatus").isEmpty());
        awaitIngestion(ws, doc);
        awaitIndex(ws, doc, "INDEXED");
        assertThat(embeddingRows(doc)).isEqualTo(before);

        mvc.perform(delete("/api/workspaces/{ws}/documents/{id}", ws, doc)).andExpect(status().isNoContent());
        assertThat(embeddingRows(doc)).isZero();
    }

    @Test
    void startupReconciliationBackfillsMissingEmbeddings() throws Exception {
        String ws = createWorkspace("Backfill");
        String doc = uploadText(ws, "notes.txt", TestFiles.prose(3));
        awaitIndex(ws, doc, "INDEXED");
        int expected = embeddingRows(doc);

        // Simulate a document ingested before embeddings existed (or under another model).
        jdbc.update("DELETE FROM chunk_embeddings WHERE document_id = ?::uuid", doc);
        assertThat(embeddingRows(doc)).isZero();

        dispatcher.reconcileEmbeddings();
        awaitIndex(ws, doc, "INDEXED");
        assertThat(embeddingRows(doc)).isEqualTo(expected);
    }

    @Test
    void databaseRejectsEmbeddingsThatDisagreeWithTheirChunk() throws Exception {
        String wsA = createWorkspace("A");
        String wsB = createWorkspace("B");
        String doc = uploadText(wsA, "a.txt", "text");
        awaitIndex(wsA, doc, "INDEXED");
        String chunkId = jdbc.queryForObject("SELECT id::text FROM document_chunks WHERE document_id = ?::uuid", String.class, doc);
        String zeros = "[" + "0,".repeat(767) + "1]";

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO chunk_embeddings (chunk_id, document_id, workspace_id, model, embedding)
                VALUES (?::uuid, ?::uuid, ?::uuid, 'other-model', ?::vector)
                """, chunkId, doc, wsB, zeros)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO chunk_embeddings (chunk_id, document_id, workspace_id, model, embedding)
                VALUES (?::uuid, ?::uuid, ?::uuid, 'other-model', '[1,2,3]'::vector)
                """, chunkId, doc, wsA)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private String embeddingCreatedAt(String documentId) {
        return jdbc.queryForObject("SELECT max(created_at)::text FROM chunk_embeddings WHERE document_id = ?::uuid",
                String.class, documentId);
    }

    private int embeddingRows(String documentId) {
        return jdbc.queryForObject("SELECT count(*) FROM chunk_embeddings WHERE document_id = ?::uuid",
                Integer.class, documentId);
    }
}
