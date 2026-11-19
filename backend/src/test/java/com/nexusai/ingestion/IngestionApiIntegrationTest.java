package com.nexusai.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nexusai.ApiTestSupport;
import com.nexusai.TestFiles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

class IngestionApiIntegrationTest extends ApiTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${nexusai.storage.local.root}")
    private Path storageRoot;

    // --- successful uploads ---

    @Test
    void textUploadIsStoredIngestedAndChunked() throws Exception {
        String ws = createWorkspace("Ingest");
        byte[] bytes = TestFiles.prose(12).getBytes(StandardCharsets.UTF_8);

        String body = upload(ws, file("rag-notes.txt", bytes))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", matchesPattern("^/api/workspaces/" + ws + "/documents/[0-9a-f-]{36}$")))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.title").value("rag-notes"))
                .andExpect(jsonPath("$.origin").value("rag-notes.txt"))
                .andExpect(jsonPath("$.sourceType").value("FILE"))
                .andExpect(jsonPath("$.mediaType").value("text/plain"))
                .andExpect(jsonPath("$.fileSizeBytes").value(bytes.length))
                .andExpect(jsonPath("$.storageKey").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        String done = awaitIngestion(ws, id);
        assertThat((String) JsonPath.read(done, "$.status")).isEqualTo("READY");
        assertThat((Object) JsonPath.read(done, "$.pageCount")).isNull();
        int chunkCount = JsonPath.read(done, "$.chunkCount");
        assertThat(chunkCount).isGreaterThan(1);

        mvc.perform(get("/api/workspaces/{ws}/documents/{id}/chunks", ws, id).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(chunkCount))
                .andExpect(jsonPath("$.items[0].index").value(0))
                .andExpect(jsonPath("$.items[0].pageNumber").isEmpty())
                .andExpect(jsonPath("$.items[0].charStart").value(0))
                .andExpect(jsonPath("$.items[0].content").value(containsString("Paragraph 1.")));

        // The original is stored byte-for-byte outside the database, under a generated key.
        String key = jdbc.queryForObject("SELECT storage_key FROM documents WHERE id = ?::uuid", String.class, id);
        String sha = jdbc.queryForObject("SELECT content_sha256 FROM documents WHERE id = ?::uuid", String.class, id);
        assertThat(key).matches(ws + "/[0-9a-f-]{36}").doesNotContain("rag-notes");
        assertThat(Files.readAllBytes(storageRoot.resolve(key))).isEqualTo(bytes);
        assertThat(sha).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    }

    @Test
    void pdfUploadKeepsPageProvenance() throws Exception {
        String ws = createWorkspace("Pdf");
        byte[] pdf = TestFiles.pdf("Introduction to transformers", "Self attention mechanism", "Results and conclusion");

        String id = JsonPath.read(upload(ws, file("paper.pdf", pdf), "Attention paper")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.title").value("Attention paper"))
                .andExpect(jsonPath("$.mediaType").value("application/pdf"))
                .andReturn().getResponse().getContentAsString(), "$.id");

        String done = awaitIngestion(ws, id);
        assertThat((String) JsonPath.read(done, "$.status")).isEqualTo("READY");
        assertThat((Integer) JsonPath.read(done, "$.pageCount")).isEqualTo(3);
        assertThat((Integer) JsonPath.read(done, "$.chunkCount")).isEqualTo(3);

        String chunks = mvc.perform(get("/api/workspaces/{ws}/documents/{id}/chunks", ws, id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Integer> pages = JsonPath.read(chunks, "$.items[*].pageNumber");
        List<String> contents = JsonPath.read(chunks, "$.items[*].content");
        assertThat(pages).containsExactly(1, 2, 3);
        assertThat(contents).containsExactly(
                "Introduction to transformers", "Self attention mechanism", "Results and conclusion");
    }

    // --- rejected uploads (nothing stored) ---

    @Test
    void invalidUploadsAreRejectedWithoutStoringAnything() throws Exception {
        String ws = createWorkspace("Rejects");

        upload(ws, file("empty.txt", new byte[0]))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("The uploaded file is empty"));
        upload(ws, file("slides.docx", TestFiles.pdf("x")))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
        upload(ws, file("fake.pdf", "just text pretending".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.detail").value(containsString("not a PDF")));
        upload(ws, file("binary.txt", new byte[] {'M', 'Z', 0, 1, 2}))
                .andExpect(status().isUnsupportedMediaType());
        upload(ws, file("ok.txt", "fine".getBytes(StandardCharsets.UTF_8)), "t".repeat(501))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
        mvc.perform(multipart("/api/workspaces/{ws}/documents", ws)
                        .file(new MockMultipartFile("attachment", "a.txt", "text/plain", new byte[] {'a'})))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/workspaces/{ws}/documents", ws)).andExpect(jsonPath("$.totalItems").value(0));
        assertThat(Files.exists(storageRoot.resolve(ws))).isFalse();
    }

    @Test
    void uploadToMissingWorkspaceIs404AndStoresNothing() throws Exception {
        UUID missing = UUID.randomUUID();
        upload(missing.toString(), file("a.txt", "hello".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isNotFound());
        assertThat(Files.exists(storageRoot.resolve(missing.toString()))).isFalse();
    }

    // --- ingestion failures ---

    @Test
    void unreadableFilesEndFailedWithAUserFacingReason() throws Exception {
        String ws = createWorkspace("Failures");
        assertFailure(ws, "scan.pdf", TestFiles.pdf((String) null), "No extractable text");
        assertFailure(ws, "locked.pdf", TestFiles.encryptedPdf("secret"), "Password-protected");
        assertFailure(ws, "broken.pdf", "%PDF-1.7\n\u0000garbage".getBytes(StandardCharsets.ISO_8859_1), "could not be read");
        assertFailure(ws, "latin1.txt", new byte[] {'c', 'a', 'f', (byte) 0xE9}, "not valid UTF-8");
    }

    @Test
    void failedDocumentCanBeRetriedAndReadyDocumentReingestsIdempotently() throws Exception {
        String ws = createWorkspace("Retry");
        String failed = assertFailure(ws, "scan.pdf", TestFiles.pdf((String) null), "No extractable text");

        mvc.perform(post("/api/workspaces/{ws}/documents/{id}/ingestion", ws, failed))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.statusDetail").isEmpty());
        assertThat((String) JsonPath.read(awaitIngestion(ws, failed), "$.status")).isEqualTo("FAILED");

        String ready = uploadAndAwait(ws, "notes.txt", TestFiles.prose(6).getBytes(StandardCharsets.UTF_8));
        int before = JsonPath.read(awaitIngestion(ws, ready), "$.chunkCount");
        mvc.perform(post("/api/workspaces/{ws}/documents/{id}/ingestion", ws, ready))
                .andExpect(status().isAccepted());
        assertThat((Integer) JsonPath.read(awaitIngestion(ws, ready), "$.chunkCount")).isEqualTo(before);
        assertThat(chunkRows(ready)).isEqualTo(before); // replaced, not duplicated
    }

    @Test
    void metadataOnlyDocumentCannotBeIngested() throws Exception {
        String ws = createWorkspace("Metadata");
        String doc = createDocument(ws, "No file");
        mvc.perform(post("/api/workspaces/{ws}/documents/{id}/ingestion", ws, doc))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/workspaces/{ws}/documents/{id}", ws, doc))
                .andExpect(jsonPath("$.status").value("REGISTERED"))
                .andExpect(jsonPath("$.fileSizeBytes").isEmpty());
    }

    // --- workspace scoping ---

    @Test
    void chunksAndIngestionControlsAreWorkspaceScoped() throws Exception {
        String wsA = createWorkspace("A");
        String wsB = createWorkspace("B");
        String docA = uploadAndAwait(wsA, "secret.txt", "Confidential plan for workspace A.".getBytes(StandardCharsets.UTF_8));

        mvc.perform(get("/api/workspaces/{ws}/documents/{id}/chunks", wsB, docA)).andExpect(status().isNotFound());
        mvc.perform(post("/api/workspaces/{ws}/documents/{id}/ingestion", wsB, docA)).andExpect(status().isNotFound());
        mvc.perform(get("/api/workspaces/{ws}/documents/{id}", wsB, docA)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/workspaces/{ws}/documents/{id}", wsB, docA)).andExpect(status().isNotFound());

        mvc.perform(get("/api/workspaces/{ws}/documents/{id}/chunks", wsA, docA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].content").value("Confidential plan for workspace A."));
        UUID chunkWorkspace = jdbc.queryForObject(
                "SELECT DISTINCT workspace_id FROM document_chunks WHERE document_id = ?::uuid", UUID.class, docA);
        assertThat(chunkWorkspace).hasToString(wsA);
    }

    @Test
    void databaseRejectsAChunkAttachedToAnotherWorkspace() throws Exception {
        String wsA = createWorkspace("A");
        String wsB = createWorkspace("B");
        String docA = uploadAndAwait(wsA, "a.txt", "text".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO document_chunks (id, document_id, workspace_id, chunk_index, char_start, char_end, content)
                VALUES (gen_random_uuid(), ?::uuid, ?::uuid, 99, 0, 1, 'x')
                """, docA, wsB)).isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- deletion ---

    @Test
    void deletingADocumentRemovesChunksAndTheStoredFile() throws Exception {
        String ws = createWorkspace("Delete");
        String doc = uploadAndAwait(ws, "notes.txt", TestFiles.prose(4).getBytes(StandardCharsets.UTF_8));
        String key = jdbc.queryForObject("SELECT storage_key FROM documents WHERE id = ?::uuid", String.class, doc);
        assertThat(chunkRows(doc)).isPositive();
        assertThat(Files.exists(storageRoot.resolve(key))).isTrue();

        mvc.perform(delete("/api/workspaces/{ws}/documents/{id}", ws, doc)).andExpect(status().isNoContent());

        assertThat(chunkRows(doc)).isZero();
        assertThat(Files.exists(storageRoot.resolve(key))).isFalse();
        mvc.perform(get("/api/workspaces/{ws}/documents/{id}/chunks", ws, doc)).andExpect(status().isNotFound());
        // The workspace is now empty and can be deleted.
        mvc.perform(delete("/api/workspaces/{ws}", ws)).andExpect(status().isNoContent());
    }

    // --- helpers ---

    private ResultActions upload(String workspaceId, MockMultipartFile file) throws Exception {
        return mvc.perform(multipart("/api/workspaces/{ws}/documents", workspaceId).file(file));
    }

    private ResultActions upload(String workspaceId, MockMultipartFile file, String title) throws Exception {
        return mvc.perform(multipart("/api/workspaces/{ws}/documents", workspaceId).file(file).param("title", title));
    }

    private static MockMultipartFile file(String name, byte[] bytes) {
        // Declared content type is deliberately generic: the server must not rely on it.
        return new MockMultipartFile("file", name, "application/octet-stream", bytes);
    }

    private String uploadAndAwait(String ws, String name, byte[] bytes) throws Exception {
        String id = JsonPath.read(upload(ws, file(name, bytes))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString(), "$.id");
        assertThat((String) JsonPath.read(awaitIngestion(ws, id), "$.status")).isEqualTo("READY");
        return id;
    }

    private String assertFailure(String ws, String name, byte[] bytes, String reason) throws Exception {
        String id = JsonPath.read(upload(ws, file(name, bytes))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString(), "$.id");
        String done = awaitIngestion(ws, id);
        assertThat((String) JsonPath.read(done, "$.status")).as(name).isEqualTo("FAILED");
        assertThat((String) JsonPath.read(done, "$.statusDetail")).as(name).contains(reason);
        assertThat(chunkRows(id)).isZero();
        return id;
    }

    private int chunkRows(String documentId) {
        return jdbc.queryForObject("SELECT count(*) FROM document_chunks WHERE document_id = ?::uuid",
                Integer.class, documentId);
    }
}
