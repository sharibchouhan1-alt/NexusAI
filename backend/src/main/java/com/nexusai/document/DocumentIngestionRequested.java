package com.nexusai.document;

import java.util.UUID;

/**
 * In-process domain event: a document's stored original is waiting for ingestion.
 * Published inside the transaction that queued it; consumers should react after commit.
 * The document module does not know who consumes it.
 */
public record DocumentIngestionRequested(UUID workspaceId, UUID documentId) {
}
