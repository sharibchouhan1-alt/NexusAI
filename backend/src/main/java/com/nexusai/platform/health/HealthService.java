package com.nexusai.platform.health;

import com.nexusai.platform.health.HealthReport.DatabaseHealth;
import com.nexusai.platform.health.HealthReport.Status;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Reports backend liveness and database readiness. The application is UP only when the
 * database is reachable and the pgvector extension (installed by Flyway) is present.
 */
@Service
public class HealthService {

    private static final Logger log = LoggerFactory.getLogger(HealthService.class);

    private final JdbcTemplate jdbc;
    private final String serviceName;
    private final Clock clock;

    public HealthService(JdbcTemplate jdbc,
                         @Value("${spring.application.name}") String serviceName) {
        this.jdbc = jdbc;
        this.serviceName = serviceName;
        this.clock = Clock.systemUTC();
    }

    public HealthReport check() {
        DatabaseHealth database = checkDatabase();
        Status overall = database.status() == Status.UP && database.pgvector() != null
                ? Status.UP
                : Status.DOWN;
        return new HealthReport(overall, serviceName, Instant.now(clock), database);
    }

    private DatabaseHealth checkDatabase() {
        try {
            List<String> versions = jdbc.queryForList(
                    "SELECT extversion FROM pg_extension WHERE extname = 'vector'", String.class);
            return new DatabaseHealth(Status.UP, versions.isEmpty() ? null : versions.getFirst());
        } catch (RuntimeException e) {
            log.warn("Database health check failed: {}", e.getMessage());
            return new DatabaseHealth(Status.DOWN, null);
        }
    }
}
