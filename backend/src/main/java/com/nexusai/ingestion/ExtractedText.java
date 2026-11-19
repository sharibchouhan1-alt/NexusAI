package com.nexusai.ingestion;

import java.util.List;

/**
 * Normalized text of a source, kept per page so provenance survives chunking.
 *
 * @param pageCount number of pages in a paged source (PDF), {@code null} for unpaged (TXT)
 */
record ExtractedText(List<PageText> pages, Integer pageCount) {

    /** @param pageNumber 1-based page, or {@code null} for an unpaged source */
    record PageText(Integer pageNumber, String text) {}

    boolean isBlank() {
        return pages.stream().allMatch(p -> p.text().isBlank());
    }
}
