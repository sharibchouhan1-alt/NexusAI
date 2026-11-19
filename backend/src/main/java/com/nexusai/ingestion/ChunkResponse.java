package com.nexusai.ingestion;

import java.util.UUID;

/**
 * A stored chunk with its provenance.
 *
 * @param pageNumber 1-based PDF page, {@code null} for plain text
 * @param charStart  offset of {@code content} in the page's (or file's) extracted text
 */
public record ChunkResponse(UUID id, int index, Integer pageNumber, int charStart, int charEnd,
                            String content) {
}
