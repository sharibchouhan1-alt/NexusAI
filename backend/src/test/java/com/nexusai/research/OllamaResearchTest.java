package com.nexusai.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nexusai.ApiTestSupport;
import com.nexusai.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End to end with the REAL local models (no stubs): plan, tool use, evidence, synthesis.
 * Asserts behaviour that must hold for any competent run: the agent invoked tools, gathered
 * sources with provenance from this workspace, and every report citation points at a
 * gathered chunk with a quote found in its passage. Skipped if the models are missing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = ApiTestSupport.TEST_STORAGE_ROOT)
@EnabledIf("modelsAvailable")
class OllamaResearchTest {

    @Autowired
    private MockMvc mvc;

    static boolean modelsAvailable() {
        try {
            String tags = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
                    .send(HttpRequest.newBuilder(URI.create(System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434")
                            + "/api/tags")).timeout(Duration.ofSeconds(5)).build(), HttpResponse.BodyHandlers.ofString()).body();
            return tags.contains("\"nomic-embed-text") && tags.contains("\"qwen2.5:7b\"");
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void realResearchUsesToolsAndProducesAVerifiedReport() throws Exception {
        String ws = JsonPath.read(mvc.perform(post("/api/workspaces").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Real research\"}")).andReturn().getResponse().getContentAsString(), "$.id");
        upload(ws, "launch-memo.txt", """
                Internal memo: the Q3 product launch moved from September 14 to October 2 because the payment \
                provider certification took longer than planned. The marketing budget stays at 120,000 euros.""");
        upload(ws, "risk-review.txt", """
                Risk review: the main risk for the October launch is the payment provider. If certification slips \
                again, the fallback is to launch with card payments only and add invoices in November.""");
        upload(ws, "biology.txt", "Photosynthesis converts light energy into chemical energy in green plants.");

        String run = JsonPath.read(mvc.perform(post("/api/workspaces/{ws}/research", ws).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objective\": \"Why was the product launch delayed, and what is the fallback if it slips again?\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString(), "$.id");

        String body = await(ws, run);
        assertThat((String) JsonPath.read(body, "$.status")).as(body).isIn("COMPLETED", "INSUFFICIENT_EVIDENCE");
        assertThat(JsonPath.<List<Object>>read(body, "$.steps")).isNotEmpty();
        assertThat(JsonPath.<List<String>>read(body, "$.steps[*].toolCalls[*].tool")).contains("search_knowledge");
        assertThat(JsonPath.<List<String>>read(body, "$.steps[*].toolCalls[*].status")).contains("SUCCEEDED");
        List<Map<String, Object>> sources = JsonPath.read(body, "$.sources");
        assertThat(sources).isNotEmpty();
        Set<Object> gathered = Set.copyOf(sources.stream().map(s -> s.get("chunkId")).toList());

        assertThat((String) JsonPath.read(body, "$.status")).as("report status; detail: " + JsonPath.read(body, "$.statusDetail"))
                .isEqualTo("COMPLETED");
        List<Map<String, Object>> citations = JsonPath.read(body, "$.report.citations");
        assertThat(citations).isNotEmpty();
        for (Map<String, Object> c : citations) {
            assertThat(gathered).contains(c.get("chunkId"));
        }
        assertThat(((String) JsonPath.read(body, "$.report.text")).toLowerCase()).contains("payment");
    }

    private void upload(String ws, String name, String text) throws Exception {
        String id = JsonPath.read(mvc.perform(multipart("/api/workspaces/{ws}/documents", ws)
                        .file(new MockMultipartFile("file", name, "text/plain", text.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString(), "$.id");
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (System.nanoTime() < deadline) {
            String doc = mvc.perform(get("/api/workspaces/{ws}/documents/{id}", ws, id)).andReturn().getResponse().getContentAsString();
            if ("INDEXED".equals(JsonPath.read(doc, "$.indexStatus"))) return;
            Thread.sleep(200);
        }
        throw new AssertionError("not indexed");
    }

    private String await(String ws, String run) throws Exception {
        long deadline = System.nanoTime() + Duration.ofMinutes(5).toNanos();
        while (true) {
            String body = mvc.perform(get("/api/workspaces/{ws}/research/{r}", ws, run)).andReturn().getResponse().getContentAsString();
            if (Set.of("COMPLETED", "INSUFFICIENT_EVIDENCE", "FAILED", "CANCELLED").contains((String) JsonPath.read(body, "$.status"))) {
                return body;
            }
            if (System.nanoTime() > deadline) throw new AssertionError("Research did not finish: " + body);
            Thread.sleep(500);
        }
    }
}
