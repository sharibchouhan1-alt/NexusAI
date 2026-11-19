package com.nexusai.citation;

import java.util.UUID;

/**
 * A verified citation, ready to persist: {@code ordinal} is the {@code [n]} marker in the
 * final answer text; {@code quote} was found verbatim (modulo case/whitespace) in {@code passage}.
 */
public record CitationDraft(int ordinal, UUID documentId, UUID chunkId, String documentTitle,
                            Integer pageNumber, int chunkIndex, int charStart, int charEnd,
                            String quote, String passage, double score) {
}
