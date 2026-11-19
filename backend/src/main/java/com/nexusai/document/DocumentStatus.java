package com.nexusai.document;

/** Document lifecycle. Every state except REGISTERED refers to an uploaded original file. */
public enum DocumentStatus {
    /** Metadata recorded; no file. */
    REGISTERED,
    /** File stored; waiting for ingestion. */
    PENDING,
    /** Ingestion (extraction + chunking) in progress. */
    PROCESSING,
    /** Chunks persisted; the document is retrievable. */
    READY,
    /** Ingestion failed; {@code statusDetail} says why. Can be retried. */
    FAILED;

    boolean isIngestionInFlight() {
        return this == PENDING || this == PROCESSING;
    }
}
