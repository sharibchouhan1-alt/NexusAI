package com.nexusai.ingestion;

import com.nexusai.document.DocumentIndexingRequested;
import com.nexusai.document.DocumentIngestionRequested;
import com.nexusai.document.DocumentService;
import com.nexusai.platform.ai.EmbeddingService;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs ingestion and indexing in-process on a bounded worker pool. Triggered after the
 * transaction that requested the work commits, periodically for indexing that waits on the
 * embedding service, and on startup for interrupted work and missing embeddings (backfill).
 * (No broker: the architecture keeps asynchronous work in-process until proven limiting.)
 */
@Component
class IngestionDispatcher {

    private static final Logger log = LoggerFactory.getLogger(IngestionDispatcher.class);
    static final String QUEUE_FULL = "The server is busy processing other files. Please retry shortly.";
    private static final int BACKLOG_BATCH = 50;

    private final IngestionPipeline pipeline;
    private final DocumentIndexer indexer;
    private final DocumentService documents;
    private final ChunkRepository chunks;
    private final EmbeddingService embeddings;
    private final TaskExecutor executor;

    IngestionDispatcher(IngestionPipeline pipeline, DocumentIndexer indexer, DocumentService documents,
                        ChunkRepository chunks, EmbeddingService embeddings,
                        @Qualifier(IngestionConfig.EXECUTOR) TaskExecutor executor) {
        this.pipeline = pipeline;
        this.indexer = indexer;
        this.documents = documents;
        this.chunks = chunks;
        this.embeddings = embeddings;
        this.executor = executor;
    }

    @TransactionalEventListener
    void onIngestionRequested(DocumentIngestionRequested event) {
        submit(event.workspaceId(), event.documentId());
    }

    @TransactionalEventListener
    void onIndexingRequested(DocumentIndexingRequested event) {
        submitIndexing(event.workspaceId(), event.documentId());
    }

    @EventListener(ApplicationReadyEvent.class)
    void recoverUnfinishedWork() {
        List<DocumentIngestionRequested> pending = documents.recoverUnfinishedIngestion();
        if (!pending.isEmpty()) {
            log.info("Resuming ingestion of {} document(s) left unfinished", pending.size());
        }
        pending.forEach(p -> submit(p.workspaceId(), p.documentId()));

        int interrupted = documents.recoverInterruptedIndexing();
        reconcileEmbeddings();
        if (interrupted > 0) {
            log.info("Resuming indexing of {} interrupted document(s)", interrupted);
        }
        retryPendingIndexing();
    }

    /**
     * Backfill/reconciliation: any READY document with chunks lacking an embedding for the
     * current model (ingested before embeddings existed, or after a model change) is queued.
     */
    void reconcileEmbeddings() {
        List<ChunkRepository.DocumentRef> missing = chunks.documentsMissingEmbeddings(embeddings.modelId());
        if (!missing.isEmpty()) {
            log.info("{} document(s) have chunks without {} embeddings; queuing for indexing",
                    missing.size(), embeddings.modelId());
        }
        missing.forEach(ref -> documents.requeueIndexing(ref.workspaceId(), ref.documentId()));
    }

    /**
     * Retries documents whose indexing is waiting (e.g. Ollama was down). Probes the
     * embedding service once first, so an outage costs one cheap call per interval.
     */
    @Scheduled(initialDelayString = "${nexusai.embedding.retry-interval}",
               fixedDelayString = "${nexusai.embedding.retry-interval}")
    void retryPendingIndexing() {
        List<DocumentIndexingRequested> backlog = documents.findIndexingBacklog(BACKLOG_BATCH);
        if (backlog.isEmpty()) {
            return;
        }
        if (!embeddings.isAvailable()) {
            log.info("Embedding service unavailable; {}{} document(s) still waiting for indexing",
                    backlog.size(), backlog.size() == BACKLOG_BATCH ? "+" : "");
            return;
        }
        log.info("Indexing {} waiting document(s)", backlog.size());
        backlog.forEach(r -> submitIndexing(r.workspaceId(), r.documentId()));
    }

    void submit(UUID workspaceId, UUID documentId) {
        try {
            executor.execute(() -> pipeline.ingest(workspaceId, documentId));
        } catch (TaskRejectedException e) {
            log.warn("Ingestion queue full; document {} marked failed", documentId);
            documents.failIngestion(workspaceId, documentId, QUEUE_FULL);
        }
    }

    void submitIndexing(UUID workspaceId, UUID documentId) {
        try {
            executor.execute(() -> indexer.index(workspaceId, documentId));
        } catch (TaskRejectedException e) {
            // Stays PENDING; the periodic retry picks it up when the pool has room.
            log.info("Ingestion queue full; indexing of document {} deferred", documentId);
        }
    }
}
