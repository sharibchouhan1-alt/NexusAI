package com.nexusai.ingestion;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** {@code nexusai.ingestion.*}: chunking and resource limits for extraction. */
@Validated
@ConfigurationProperties("nexusai.ingestion")
record IngestionProperties(
        @Min(100) int chunkSize,
        @Min(0) int chunkOverlap,
        @Min(1) int maxPages,
        @Min(1) long maxTextChars,
        @Min(1) int workerThreads,
        @Min(1) int queueCapacity) {

    /** Overlap below half the chunk size guarantees the chunker always moves forward. */
    @AssertTrue(message = "chunk-overlap must be less than half of chunk-size")
    boolean isOverlapValid() {
        return chunkOverlap * 2 < chunkSize;
    }
}
