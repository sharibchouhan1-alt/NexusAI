package com.nexusai.platform.health;

import java.time.Instant;

/** Transport-neutral result of a health check. */
public record HealthReport(Status status, String service, Instant timestamp, DatabaseHealth database) {

    public enum Status { UP, DOWN }

    /**
     * @param status   whether the database answered a query
     * @param pgvector installed pgvector extension version, or {@code null} if absent/unknown
     */
    public record DatabaseHealth(Status status, String pgvector) {}
}
