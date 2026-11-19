package com.nexusai.platform.ai;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code nexusai.embedding.*}
 *
 * @param dimensions     expected vector length; must match the {@code vector(N)} column
 * @param documentPrefix prepended to chunk text (nomic-embed-text task prefix)
 * @param queryPrefix    prepended to search queries
 * @param retryInterval  how often work waiting for an unavailable provider is retried
 */
@Validated
@ConfigurationProperties("nexusai.embedding")
public record EmbeddingProperties(
        @Min(1) int dimensions,
        @Min(1) int batchSize,
        @NotNull String documentPrefix,
        @NotNull String queryPrefix,
        @NotNull Duration retryInterval) {
}
