package com.nexusai.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nexusai.ApiTestSupport;
import com.nexusai.TestEmbeddingModel;
import com.nexusai.TestFiles;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

class SemanticSearchIntegrationTest extends ApiTestSupport {

    private static final String PLANTS = "Photosynthesis lets plants convert sunlight, water and carbon dioxide into glucose. "
            + "Chlorophyll in green leaves absorbs light energy.";
    private static final String STARS = "Stars fuse hydrogen into helium in their cores. "
            + "Supernova explosions scatter heavy elements across galaxies.";
    private static final String BREAD = "Bread dough rises because yeast ferments sugar and releases gas. "
            + "Knead the dough, let it rest, then bake it in a hot oven.";

    @Autowired
    private TestEmbeddingModel embeddingModel;

    @Test
    void returnsRankedPassagesWithDocumentAndChunkProvenance() throws Exception {
        String ws = createWorkspace("Search");
        String plants = indexed(ws, "plants.txt", PLANTS);
        indexed(ws, "stars.txt", STARS);
        indexed(ws, "bread.txt", BREAD);

        String body = search(ws, "chlorophyll leaves sunlight")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("chlorophyll leaves sunlight"))
                .andExpect(jsonPath("$.model").value("ollama:nomic-embed-text"))
                .andExpect(jsonPath("$.coverage.indexed").value(3))
                .andExpect(jsonPath("$.coverage.pending").value(0))
                .andExpect(jsonPath("$.results.length()").value(3))
                .andExpect(jsonPath("$.results[0].rank").value(1))
                .andExpect(jsonPath("$.results[0].documentId").value(plants))
                .andExpect(jsonPath("$.results[0].documentTitle").value("plants"))
                .andExpect(jsonPath("$.results[0].documentOrigin").value("plants.txt"))
                .andExpect(jsonPath("$.results[0].mediaType").value("text/plain"))
                .andExpect(jsonPath("$.results[0].chunkIndex").value(0))
                .andExpect(jsonPath("$.results[0].charStart").value(0))
                .andExpect(jsonPath("$.results[0].content").value(PLANTS))
                .andReturn().getResponse().getContentAsString();

        List<Double> scores = JsonPath.read(body, "$.results[*].score");
        assertThat(scores).isSortedAccordingTo((a, b) -> Double.compare(b, a));
        assertThat(scores.getFirst()).isGreaterThan(scores.get(1));
        assertThat(JsonPath.<List<Integer>>read(body, "$.results[*].rank")).containsExactly(1, 2, 3);

        search(ws, "chlorophyll", 1).andExpect(jsonPath("$.results.length()").value(1));
    }

    @Test
    void pdfResultsCarryPageNumbers() throws Exception {
        String ws = createWorkspace("Pdf search");
        String id = JsonPath.read(mvc.perform(multipart("/api/workspaces/{ws}/documents", ws)
                        .file(new MockMultipartFile("file", "astro.pdf", "application/pdf",
                                TestFiles.pdf("Introduction to astronomy", "Stars fuse hydrogen into helium", "Galaxies and dark matter"))))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString(), "$.id");
        awaitIndex(ws, id, "INDEXED");

        search(ws, "hydrogen helium fusion")
                .andExpect(jsonPath("$.results[0].pageNumber").value(2))
                .andExpect(jsonPath("$.results[0].content").value("Stars fuse hydrogen into helium"))
                .andExpect(jsonPath("$.results[0].mediaType").value("application/pdf"));
    }

    @Test
    void resultsNeverCrossWorkspaces() throws Exception {
        String wsA = createWorkspace("A");
        String wsB = createWorkspace("B");
        indexed(wsA, "secret.txt", "Confidential merger plan for workspace A: acquire Initech in March.");
        String own = indexed(wsB, "public.txt", "Public roadmap for workspace B.");

        String body = search(wsB, "confidential merger plan acquire Initech")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(body, "$.results[*].documentId")).containsOnly(own);
        assertThat(JsonPath.<List<String>>read(body, "$.results[*].content")).noneMatch(c -> c.contains("Initech"));
    }

    @Test
    void emptyWorkspaceReturnsNoResults() throws Exception {
        String ws = createWorkspace("Empty");
        search(ws, "anything")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(0))
                .andExpect(jsonPath("$.coverage.indexed").value(0));
    }

    @Test
    void invalidRequestsAreRejected() throws Exception {
        String ws = createWorkspace("Validation");
        mvc.perform(get("/api/workspaces/{ws}/search", ws)).andExpect(status().isBadRequest());
        search(ws, "   ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("q"));
        search(ws, "x".repeat(1001)).andExpect(status().isBadRequest());
        search(ws, "ok", 0).andExpect(status().isBadRequest());
        search(ws, "ok", 51).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("limit must be at most 50"));
        search(UUID.randomUUID().toString(), "ok").andExpect(status().isNotFound());
    }

    @Test
    void embeddingServiceDowntimeIsA503WithRetryHint() throws Exception {
        String ws = createWorkspace("Downtime");
        embeddingModel.setAvailable(false);
        try {
            search(ws, "anything")
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(header().exists("Retry-After"))
                    .andExpect(jsonPath("$.retryable").value(true))
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Ollama is running")));
            // An unknown workspace is still a 404, not a 503.
            search(UUID.randomUUID().toString(), "anything").andExpect(status().isNotFound());
        } finally {
            embeddingModel.setAvailable(true);
        }
        search(ws, "anything").andExpect(status().isOk());
    }

    // --- helpers ---

    private String indexed(String ws, String filename, String text) throws Exception {
        String id = uploadText(ws, filename, text);
        awaitIndex(ws, id, "INDEXED");
        return id;
    }

    private org.springframework.test.web.servlet.ResultActions search(String ws, String q) throws Exception {
        return mvc.perform(get("/api/workspaces/{ws}/search", ws).param("q", q));
    }

    private org.springframework.test.web.servlet.ResultActions search(String ws, String q, int limit) throws Exception {
        return mvc.perform(get("/api/workspaces/{ws}/search", ws).param("q", q).param("limit", String.valueOf(limit)));
    }
}
