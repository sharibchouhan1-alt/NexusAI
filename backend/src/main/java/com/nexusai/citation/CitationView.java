package com.nexusai.citation;

import java.util.UUID;

/**
 * A persisted citation as shown to clients: where the evidence came from (document, chunk,
 * page, character range) and the verified quote within the passage snapshot.
 *
 * @param ordinal the {@code [n]} marker number in the answer text
 * @param passage the cited chunk text as it was when the answer was produced
 * @param score   retrieval similarity of the passage to the question
 */
public record CitationView(int ordinal, String sourceKind, UUID documentId, UUID chunkId, String documentTitle,
                           Integer pageNumber, int chunkIndex, int charStart, int charEnd,
                           String quote, String passage, double score) {
}
