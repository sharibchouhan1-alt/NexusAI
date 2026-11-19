package com.nexusai.platform.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexusai.TestcontainersConfiguration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/**
 * Full-stack check against a real PostgreSQL + pgvector container: Flyway runs the
 * migrations on startup and /api/health reports the installed pgvector version.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class HealthIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayInstallsPgvector() {
        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success",
                Integer.class);
        assertThat(applied).isEqualTo(1);

        // The vector type is usable.
        String distance = jdbc.queryForObject(
                "SELECT ('[1,2,3]'::vector <-> '[1,2,4]'::vector)::text", String.class);
        assertThat(distance).isEqualTo("1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void healthEndpointReportsUp() {
        Map<String, Object> body = RestClient.create("http://localhost:" + port)
                .get().uri("/api/health")
                .retrieve()
                .body(Map.class);

        assertThat(body).containsEntry("status", "UP");
        Map<String, Object> database = (Map<String, Object>) body.get("database");
        assertThat(database).containsEntry("status", "UP");
        assertThat(database.get("pgvector")).isNotNull();
    }
}
