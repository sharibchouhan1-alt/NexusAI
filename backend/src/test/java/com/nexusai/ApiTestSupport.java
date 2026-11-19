package com.nexusai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
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
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = ApiTestSupport.TEST_STORAGE_ROOT)
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
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (true) {
            String body = mvc.perform(get("/api/workspaces/{ws}/documents/{id}", workspaceId, documentId))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String state = JsonPath.read(body, "$.status");
            if (Set.of("READY", "FAILED").contains(state)) {
                return body;
            }
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Ingestion did not finish in time; last state " + state);
            }
            Thread.sleep(100);
        }
    }
}
