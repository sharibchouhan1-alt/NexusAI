package com.nexusai.platform.health;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexusai.platform.health.HealthReport.DatabaseHealth;
import com.nexusai.platform.health.HealthReport.Status;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Transport-only test: status code mapping and JSON shape. */
@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private HealthService healthService;

    @Test
    void returns200WhenUp() throws Exception {
        when(healthService.check()).thenReturn(new HealthReport(
                Status.UP, "nexusai-backend", Instant.parse("2026-01-01T00:00:00Z"),
                new DatabaseHealth(Status.UP, "0.8.0")));

        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("nexusai-backend"))
                .andExpect(jsonPath("$.database.status").value("UP"))
                .andExpect(jsonPath("$.database.pgvector").value("0.8.0"))
                .andExpect(jsonPath("$.up").doesNotExist());
    }

    @Test
    void returns503WhenDown() throws Exception {
        when(healthService.check()).thenReturn(new HealthReport(
                Status.DOWN, "nexusai-backend", Instant.now(), new DatabaseHealth(Status.DOWN, null)));

        mvc.perform(get("/api/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.database.status").value("DOWN"));
    }
}
