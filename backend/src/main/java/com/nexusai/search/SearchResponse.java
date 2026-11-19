package com.nexusai.search;

import com.nexusai.document.SearchCoverage;
import java.util.List;
import java.util.UUID;

/**
 * Ranked passages for a query, most similar first.
 *
 * @param model    embedding model that produced the vectors compared
 * @param coverage how much of the workspace was searchable when the query ran
 */
public record SearchResponse(String query, String model, List<Result> results, SearchCoverage coverage) {

    /**
     * One passage with full provenance.
     *
     * @param score      cosine similarity in [-1, 1]; higher is more similar
     * @param pageNumber 1-based PDF page, null for plain text
     * @param charStart  offset of {@code content} in that page's (or file's) extracted text
     */
    public record Result(int rank, double score,
                         UUID documentId, String documentTitle, String documentOrigin, String mediaType,
                         UUID chunkId, int chunkIndex, Integer pageNumber, int charStart, int charEnd,
                         String content) {}
}
