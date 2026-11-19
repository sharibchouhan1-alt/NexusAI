package com.nexusai.search;

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
 * End to end with the REAL local model (Ollama nomic-embed-text, 768 dimensions): no stub.
 * The query shares no keywords with the expected passage, so a pass requires actual
 * semantic similarity. Skipped automatically when Ollama or the model is not available.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = ApiTestSupport.TEST_STORAGE_ROOT)
@EnabledIf("ollamaWithModelIsAvailable")
class OllamaSemanticSearchTest {

    @Autowired
    private MockMvc mvc;

    static boolean ollamaWithModelIsAvailable() {
        String base = System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434");
        try {
            HttpResponse<String> response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
                    .send(HttpRequest.newBuilder(URI.create(base + "/api/embed"))
                                    .timeout(Duration.ofSeconds(30))
                                    .POST(HttpRequest.BodyPublishers.ofString("{\"model\":\"nomic-embed-text\",\"input\":[\"ping\"]}"))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void findsPassagesByMeaningNotKeywords() throws Exception {
        String ws = JsonPath.read(mvc.perform(post("/api/workspaces")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Real embeddings\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        String plants = upload(ws, "biology.txt",
                "Photosynthesis: green plants capture energy from the sun with chlorophyll and turn it into sugar.");
        String markets = upload(ws, "markets.txt", "The stock market fell sharply as investors sold shares amid inflation fears.");
        String baking = upload(ws, "baking.txt", "The recipe calls for flour, eggs and butter; bake the cake for thirty minutes.");
        for (String id : List.of(plants, markets, baking)) {
            awaitIndexed(ws, id);
        }

        String body = mvc.perform(get("/api/workspaces/{ws}/search", ws).param("q", "how do vegetables turn light into food"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(body, "$.model")).isEqualTo("ollama:nomic-embed-text");
        assertThat((String) JsonPath.read(body, "$.results[0].documentId")).isEqualTo(plants);
        List<Double> scores = JsonPath.read(body, "$.results[*].score");
        assertThat(scores.getFirst()).isGreaterThan(scores.get(1));
    }

    private String upload(String ws, String name, String text) throws Exception {
        return JsonPath.read(mvc.perform(multipart("/api/workspaces/{ws}/documents", ws)
                        .file(new MockMultipartFile("file", name, "text/plain", text.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private void awaitIndexed(String ws, String id) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (System.nanoTime() < deadline) {
            String body = mvc.perform(get("/api/workspaces/{ws}/documents/{id}", ws, id))
                    .andReturn().getResponse().getContentAsString();
            if ("INDEXED".equals(JsonPath.read(body, "$.indexStatus"))) return;
            Thread.sleep(200);
        }
        throw new AssertionError("Document was not indexed by the real model in time");
    }
}
