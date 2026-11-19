package com.nexusai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.mock.web.MockMultipartFile;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Base for API integration tests: full context, real database, MockMvc transport. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestEmbeddingConfiguration.class})
@TestPropertySource(properties = {
        ApiTestSupport.TEST_STORAGE_ROOT,
        "nexusai.embedding.retry-interval=300ms",
        // The lexical test embedder's similarities are lower than real semantic ones.
        "nexusai.rag.min-score=0.05",
})
public abstract class ApiTestSupport {

    /** Uploaded originals go to a throwaway directory, never the developer's real storage. */
    public static final String TEST_STORAGE_ROOT =
            "nexusai.storage.local.root=${java.io.tmpdir}/nexusai-test-storage";

    @Autowired
    protected MockMvc mvc;

    protected String createWorkspace(String name) throws Exception {
        String body = mvc.perform(post("/api/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    protected String createDocument(String workspaceId, String title) throws Exception {
        String body = mvc.perform(post("/api/workspaces/{ws}/documents", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","sourceType":"FILE","mediaType":"application/pdf","origin":"paper.pdf"}
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    /** Polls the document until ingestion settles (READY or FAILED); returns its JSON. */
    protected String awaitIngestion(String workspaceId, String documentId) throws Exception {
        return awaitDocument(workspaceId, documentId, "$.status", Set.of("READY", "FAILED"));
    }

    /** Polls the document until its search index reaches one of {@code states}; returns its JSON. */
    protected String awaitIndex(String workspaceId, String documentId, String... states) throws Exception {
        return awaitDocument(workspaceId, documentId, "$.indexStatus", Set.of(states));
    }

    /** Uploads a UTF-8 text file and returns the new document's id. */
    protected String uploadText(String workspaceId, String filename, String text) throws Exception {
        String body = mvc.perform(multipart("/api/workspaces/{ws}/documents", workspaceId)
                        .file(new MockMultipartFile("file", filename, "text/plain", text.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String awaitDocument(String workspaceId, String documentId, String path, Set<String> states)
            throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (true) {
            String body = mvc.perform(get("/api/workspaces/{ws}/documents/{id}", workspaceId, documentId))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            Object state = JsonPath.read(body, path);
            if (state != null && states.contains(state.toString())) {
                return body;
            }
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Document " + path + " did not reach " + states + "; last value " + state);
            }
            Thread.sleep(100);
        }
    }
}
