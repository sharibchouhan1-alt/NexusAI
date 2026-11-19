package com.nexusai.ingestion;

import com.nexusai.document.DocumentService;
import com.nexusai.ingestion.ChunkRepository.ChunkText;
import com.nexusai.platform.ai.EmbeddingContractException;
import com.nexusai.platform.ai.EmbeddingProperties;
import com.nexusai.platform.ai.EmbeddingService;
import com.nexusai.platform.ai.EmbeddingUnavailableException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Builds the search index of one READY document: embeds every chunk that has no embedding
 * for the current model, batch by batch. Each batch is its own transaction, so an
 * interruption (Ollama down, restart) resumes where it stopped instead of starting over.
 */
@Component
class DocumentIndexer {

    private static final Logger log = LoggerFactory.getLogger(DocumentIndexer.class);
    static final String WAITING_FOR_EMBEDDINGS =
            "Waiting for the embedding service (Ollama). Indexing retries automatically.";

    private final DocumentService documents;
    private final ChunkRepository chunks;
    private final EmbeddingService embeddings;
    private final EmbeddingProperties properties;

    DocumentIndexer(DocumentService documents, ChunkRepository chunks, EmbeddingService embeddings,
                    EmbeddingProperties properties) {
        this.documents = documents;
        this.chunks = chunks;
        this.embeddings = embeddings;
        this.properties = properties;
    }

    /** @return false if the embedding service was unavailable (caller may stop sending work) */
    boolean index(UUID workspaceId, UUID documentId) {
        if (!documents.beginIndexing(workspaceId, documentId)) {
            return true; // not pending (already indexed, being indexed, or gone)
        }
        String model = embeddings.modelId();
        long started = System.nanoTime();
        int embedded = 0;
        try {
            while (true) {
                List<ChunkText> batch = chunks.findUnembedded(workspaceId, documentId, model, properties.batchSize());
                if (batch.isEmpty()) {
                    break;
                }
                List<float[]> vectors = embeddings.embedDocuments(batch.stream().map(ChunkText::content).toList());
                if (chunks.saveEmbeddings(workspaceId, documentId, model, batch, vectors) == 0) {
                    throw new IllegalStateException("No embeddings could be stored for document " + documentId);
                }
                embedded += batch.size();
            }
            documents.completeIndexing(workspaceId, documentId);
            log.info("Indexed document {}: {} new embeddings ({}) in {} ms", documentId, embedded, model,
                    (System.nanoTime() - started) / 1_000_000);
            return true;
        } catch (EmbeddingUnavailableException e) {
            log.warn("Embedding service unavailable while indexing document {} ({} embedded so far): {}",
                    documentId, embedded, rootMessage(e));
            documents.deferIndexing(workspaceId, documentId, WAITING_FOR_EMBEDDINGS);
            return false;
        } catch (EmbeddingContractException e) {
            log.error("Indexing of document {} failed: {}", documentId, e.getMessage());
            documents.failIndexing(workspaceId, documentId, e.getMessage());
            return true;
        } catch (DataIntegrityViolationException e) {
            // Chunks were replaced or the document deleted meanwhile; whoever did that owns the next run.
            log.info("Indexing of document {} superseded: {}", documentId, e.getMostSpecificCause().getMessage());
            documents.deferIndexing(workspaceId, documentId, null);
            return true;
        } catch (RuntimeException e) {
            log.error("Indexing of document {} failed unexpectedly", documentId, e);
            documents.failIndexing(workspaceId, documentId, "An unexpected error occurred while indexing.");
            return true;
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) root = root.getCause();
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
