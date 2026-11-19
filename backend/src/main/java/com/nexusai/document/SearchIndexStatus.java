package com.nexusai.document;

/**
 * Whether an ingested (READY) document is searchable. {@code null} on a document until its
 * chunks exist.
 */
public enum SearchIndexStatus {
    /** Waiting for embeddings (new, backfill, or the embedding service was unavailable). */
    PENDING,
    /** Embeddings are being computed. */
    INDEXING,
    /** Every chunk has an embedding; the document is searchable. */
    INDEXED,
    /** Indexing failed for a reason that retrying will not fix by itself (see detail). */
    FAILED
}
