package com.nexusai.ingestion;

import com.nexusai.document.DocumentIngestionRequested;
import com.nexusai.document.DocumentService;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs ingestion in-process on a bounded worker pool. Triggered after the transaction that
 * stored a document commits, and on startup for work interrupted by a shutdown or crash.
 * (No broker: the architecture keeps asynchronous work in-process until proven limiting.)
 */
@Component
class IngestionDispatcher {

    private static final Logger log = LoggerFactory.getLogger(IngestionDispatcher.class);
    static final String QUEUE_FULL = "The server is busy processing other files. Please retry shortly.";

    private final IngestionPipeline pipeline;
    private final DocumentService documents;
    private final TaskExecutor executor;

    IngestionDispatcher(IngestionPipeline pipeline, DocumentService documents,
                        @Qualifier(IngestionConfig.EXECUTOR) TaskExecutor executor) {
        this.pipeline = pipeline;
        this.documents = documents;
        this.executor = executor;
    }

    @TransactionalEventListener
    void onIngestionRequested(DocumentIngestionRequested event) {
        submit(event.workspaceId(), event.documentId());
    }

    @EventListener(ApplicationReadyEvent.class)
    void recoverUnfinishedWork() {
        List<DocumentIngestionRequested> pending = documents.recoverUnfinishedIngestion();
        if (!pending.isEmpty()) {
            log.info("Resuming ingestion of {} document(s) left unfinished", pending.size());
        }
        pending.forEach(p -> submit(p.workspaceId(), p.documentId()));
    }

    void submit(UUID workspaceId, UUID documentId) {
        try {
            executor.execute(() -> pipeline.ingest(workspaceId, documentId));
        } catch (TaskRejectedException e) {
            log.warn("Ingestion queue full; document {} marked failed", documentId);
            documents.failIngestion(workspaceId, documentId, QUEUE_FULL);
        }
    }
}
