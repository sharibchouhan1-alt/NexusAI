package com.nexusai.tool;

import java.util.UUID;

/**
 * A passage of a workspace document with full provenance.
 *
 * @param score retrieval similarity; {@code null} when read as context rather than searched
 */
public record Passage(UUID documentId, UUID chunkId, String documentTitle, Integer pageNumber,
                      int chunkIndex, int charStart, int charEnd, String content, Double score) {
}
