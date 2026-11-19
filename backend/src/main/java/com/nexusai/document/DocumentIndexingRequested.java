package com.nexusai.document;

import java.util.UUID;

/**
 * In-process domain event: a READY document's search index should be (re)built.
 * Published inside the requesting transaction; consumers react after commit.
 */
public record DocumentIndexingRequested(UUID workspaceId, UUID documentId) {
}
