package com.nexusai.search;

import com.nexusai.document.DocumentService;
import com.nexusai.document.DocumentService.DocumentSummary;
import com.nexusai.document.SearchCoverage;
import com.nexusai.ingestion.ChunkSearch;
import com.nexusai.ingestion.ChunkSearch.ChunkMatch;
import com.nexusai.platform.ai.EmbeddingProperties;
import com.nexusai.platform.ai.EmbeddingService;
import com.nexusai.platform.ai.EmbeddingUnavailableException;
import com.nexusai.platform.web.ServiceUnavailableException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Workspace-scoped semantic retrieval: embed the query, find the nearest chunks inside the
 * workspace, attach document provenance. Returns candidates only; it never generates text.
 */
@Service
public class SearchService {

    private final EmbeddingService embeddings;
    private final ChunkSearch chunks;
    private final DocumentService documents;
    private final EmbeddingProperties embeddingProperties;

    SearchService(EmbeddingService embeddings, ChunkSearch chunks, DocumentService documents,
                  EmbeddingProperties embeddingProperties) {
        this.embeddings = embeddings;
        this.chunks = chunks;
        this.documents = documents;
        this.embeddingProperties = embeddingProperties;
    }

    public SearchResponse search(UUID workspaceId, String query, int limit) {
        // Workspace check first: a missing workspace is a 404 even when Ollama is down.
        SearchCoverage coverage = documents.searchCoverage(workspaceId);
        String text = query.strip();
        try {
            return new SearchResponse(text, embeddings.modelId(), retrieve(workspaceId, text, limit), coverage);
        } catch (EmbeddingUnavailableException e) {
            throw new ServiceUnavailableException("Semantic search is temporarily unavailable: " + e.getMessage(),
                    embeddingProperties.retryInterval(), e);
        }
    }

    /**
     * Published retrieval for other modules (e.g. RAG): the {@code limit} passages of the
     * workspace most similar to {@code query}, most similar first, with provenance. The
     * caller has already established that the workspace exists and may be accessed.
     *
     * @throws EmbeddingUnavailableException if the query cannot be embedded right now
     */
    public List<SearchResponse.Result> retrieve(UUID workspaceId, String query, int limit) {
        float[] vector = embeddings.embedQuery(query.strip());
        List<ChunkMatch> matches = chunks.nearest(workspaceId, embeddings.modelId(), vector, limit);
        Map<UUID, DocumentSummary> summaries = documents.summaries(workspaceId,
                matches.stream().map(ChunkMatch::documentId).collect(Collectors.toSet()));

        List<SearchResponse.Result> results = new ArrayList<>(matches.size());
        for (ChunkMatch m : matches) {
            DocumentSummary doc = summaries.get(m.documentId());
            if (doc == null) {
                continue; // deleted between the vector query and now
            }
            results.add(new SearchResponse.Result(results.size() + 1, 1.0 - m.distance(),
                    doc.id(), doc.title(), doc.origin(), doc.mediaType(),
                    m.chunkId(), m.chunkIndex(), m.pageNumber(), m.charStart(), m.charEnd(), m.content()));
        }
        return results;
    }
}
