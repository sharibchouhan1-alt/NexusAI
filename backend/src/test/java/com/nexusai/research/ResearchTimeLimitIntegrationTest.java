package com.nexusai.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nexusai.ApiTestSupport;
import com.nexusai.TestChatModel;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/** A run that exceeds its wall-clock budget stops at the next checkpoint and fails (resumable). */
@TestPropertySource(properties = "nexusai.research.max-duration=2s")
class ResearchTimeLimitIntegrationTest extends ApiTestSupport {

    @Autowired
    private TestChatModel chatModel;

    @AfterEach
    void restore() {
        chatModel.reset();
    }

    @Test
    void runsStopAtTheirTimeLimit() throws Exception {
        String ws = createWorkspace("Slow");
        String doc = uploadText(ws, "notes.txt", "Photosynthesis converts light energy into chemical energy.");
        awaitIndex(ws, doc, "INDEXED");
        ResearchScript script = new ResearchScript();
        script.agentOverride = prompt -> {
            try {
                Thread.sleep(800); // a slow local model
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "{\"action\": \"search_knowledge\", \"arguments\": {\"query\": \"photosynthesis " + System.nanoTime()
                    + "\"}, \"reason\": \"more\"}";
        };
        chatModel.setResponder(script);

        String run = JsonPath.read(mvc.perform(post("/api/workspaces/{ws}/research", ws).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objective\": \"How does photosynthesis convert light energy?\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString(), "$.id");

        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        String body;
        do {
            Thread.sleep(100);
            body = mvc.perform(get("/api/workspaces/{ws}/research/{r}", ws, run)).andReturn().getResponse().getContentAsString();
        } while (!"FAILED".equals(JsonPath.read(body, "$.status")) && System.nanoTime() < deadline);

        assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("FAILED");
        assertThat((String) JsonPath.read(body, "$.statusDetail")).contains("time limit");
        assertThat(script.synthesisCalls.get()).isZero();
        assertThat((Integer) JsonPath.read(body, "$.toolCallCount")).isLessThan(8); // stopped early, not by the budget
    }
}
