package com.nexusai.ingestion;

import com.nexusai.document.DocumentService;
import com.nexusai.document.DocumentService.IngestionSource;
import com.nexusai.ingestion.TextChunker.TextChunk;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * One ingestion run: claim → read original → extract → chunk → persist → mark READY.
 * Runs on a worker thread and holds no transaction across steps: each persisted step is its
 * own short transaction, so a crash leaves an explicit, recoverable status.
 */
@Component
class IngestionPipeline {

    private static final Logger log = LoggerFactory.getLogger(IngestionPipeline.class);
    static final String UNEXPECTED_FAILURE = "An unexpected error occurred while processing the file.";

    private final DocumentService documents;
    private final TextExtractor extractor;
    private final TextChunker chunker;
    private final ChunkRepository chunks;

    IngestionPipeline(DocumentService documents, TextExtractor extractor, TextChunker chunker,
                      ChunkRepository chunks) {
        this.documents = documents;
        this.extractor = extractor;
        this.chunker = chunker;
        this.chunks = chunks;
    }

    void ingest(UUID workspaceId, UUID documentId) {
        Optional<IngestionSource> claimed = documents.beginIngestion(workspaceId, documentId);
        if (claimed.isEmpty()) {
            log.debug("Document {} is not pending ingestion; skipping", documentId);
            return;
        }
        IngestionSource source = claimed.get();
        long started = System.nanoTime();
        try {
            ExtractedText text;
            try (InputStream original = documents.openOriginal(workspaceId, documentId)) {
                text = extractor.extract(source.mediaType(), original);
            }
            List<TextChunk> pieces = chunker.chunk(text);
            chunks.replace(workspaceId, documentId, pieces);
            documents.completeIngestion(workspaceId, documentId, text.pageCount(), pieces.size());
            log.info("Ingested document {}: {} chunks, pages={}, {} ms", documentId, pieces.size(),
                    text.pageCount() == null ? "n/a" : text.pageCount(), (System.nanoTime() - started) / 1_000_000);
        } catch (IngestionException e) {
            log.info("Ingestion of document {} failed: {}", documentId, e.getMessage());
            documents.failIngestion(workspaceId, documentId, e.getMessage());
        } catch (DataIntegrityViolationException e) {
            // Most likely the document was deleted while being processed (chunk FK).
            log.info("Ingestion of document {} aborted: {}", documentId, e.getMostSpecificCause().getMessage());
            documents.failIngestion(workspaceId, documentId, UNEXPECTED_FAILURE);
        } catch (Exception e) {
            log.error("Ingestion of document {} failed unexpectedly", documentId, e);
            documents.failIngestion(workspaceId, documentId, UNEXPECTED_FAILURE);
        }
    }
}
