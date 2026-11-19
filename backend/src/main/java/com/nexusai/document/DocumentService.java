package com.nexusai.document;

import com.nexusai.platform.storage.FileStorage;
import com.nexusai.platform.storage.FileStorage.StoredFile;
import com.nexusai.platform.web.ConflictException;
import com.nexusai.platform.web.PageResponse;
import com.nexusai.platform.web.ResourceNotFoundException;
import com.nexusai.workspace.WorkspaceService;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Published interface of the document module. Every operation takes the workspace id
 * explicitly; a document in another workspace is indistinguishable from a missing one.
 *
 * <p>The document module owns the original file and the document lifecycle. It does not
 * know about ingestion: it announces {@link DocumentIngestionRequested} and exposes the
 * lifecycle transitions below for whichever module processes the file.
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final int MAX_STATUS_DETAIL = 1000;

    private final DocumentRepository repository;
    private final WorkspaceService workspaces;
    private final FileStorage storage;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactions;

    DocumentService(DocumentRepository repository, WorkspaceService workspaces, FileStorage storage,
                    ApplicationEventPublisher events, TransactionTemplate transactions) {
        this.repository = repository;
        this.workspaces = workspaces;
        this.storage = storage;
        this.events = events;
        this.transactions = transactions;
    }

    // --- metadata ---

    @Transactional
    public DocumentResponse register(UUID workspaceId, CreateDocumentRequest request) {
        workspaces.requireExists(workspaceId);
        Document document = new Document(workspaceId, request.title().strip(),
                request.sourceType(), normalize(request.mediaType()), normalize(request.origin()));
        try {
            return DocumentResponse.from(repository.saveAndFlush(document));
        } catch (DataIntegrityViolationException e) {
            // Workspace deleted concurrently between the check and the insert.
            throw new ResourceNotFoundException("Workspace", workspaceId);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentResponse> list(UUID workspaceId, int page, int size) {
        workspaces.requireExists(workspaceId);
        var pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        return PageResponse.of(repository.findByWorkspaceId(workspaceId, pageable),
                DocumentResponse::from);
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(UUID workspaceId, UUID documentId) {
        return DocumentResponse.from(find(workspaceId, documentId));
    }

    @Transactional
    public DocumentResponse update(UUID workspaceId, UUID documentId, UpdateDocumentRequest request) {
        Document document = find(workspaceId, documentId);
        document.retitle(request.title().strip());
        return DocumentResponse.from(repository.saveAndFlush(document));
    }

    /** Deletes the document; chunks cascade in the database, the original file after commit. */
    @Transactional
    public void delete(UUID workspaceId, UUID documentId) {
        Document document = find(workspaceId, documentId);
        repository.delete(document);
        if (document.hasFile()) {
            String key = document.getStorageKey();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteStoredFile(key);
                }
            });
        }
    }

    // --- upload ---

    /**
     * Validates and stores an uploaded PDF/TXT, records it as PENDING and requests ingestion.
     * The file is written before the (short) database transaction; if the insert fails the
     * stored file is removed again.
     */
    public DocumentResponse upload(UUID workspaceId, MultipartFile file, String requestedTitle) {
        workspaces.requireExists(workspaceId);

        UploadInspector.Inspection inspection;
        StoredFile stored;
        try {
            try (InputStream head = file.getInputStream()) {
                inspection = UploadInspector.inspect(file.getOriginalFilename(), file.getSize(), head);
            }
            try (InputStream content = file.getInputStream()) {
                stored = storage.store(workspaceId.toString(), content);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store the uploaded file", e);
        }

        String title = requestedTitle == null || requestedTitle.isBlank()
                ? UploadInspector.titleFrom(inspection.safeFilename())
                : requestedTitle.strip();
        try {
            return transactions.execute(status -> {
                Document document = repository.saveAndFlush(Document.uploaded(workspaceId, title,
                        inspection.mediaType(), inspection.safeFilename(),
                        stored.key(), stored.sizeBytes(), stored.sha256()));
                events.publishEvent(new DocumentIngestionRequested(workspaceId, document.getId()));
                return DocumentResponse.from(document);
            });
        } catch (RuntimeException e) {
            deleteStoredFile(stored.key());
            if (e instanceof DataIntegrityViolationException) {
                throw new ResourceNotFoundException("Workspace", workspaceId);
            }
            throw e;
        }
    }

    /** Queues a stored document for ingestion again (e.g. after a failure). */
    @Transactional
    public DocumentResponse requestIngestion(UUID workspaceId, UUID documentId) {
        Document document = find(workspaceId, documentId);
        if (!document.hasFile()) {
            throw new ConflictException("Document " + documentId + " has no uploaded file to ingest");
        }
        if (document.getStatus().isIngestionInFlight()) {
            throw new ConflictException("Document " + documentId + " is already being ingested");
        }
        document.queueIngestion();
        repository.saveAndFlush(document);
        events.publishEvent(new DocumentIngestionRequested(workspaceId, documentId));
        return DocumentResponse.from(document);
    }

    // --- lifecycle transitions used by the ingestion process ---

    /** What a processor needs to know about the original file. */
    public record IngestionSource(UUID workspaceId, UUID documentId, String mediaType) {}

    /**
     * Atomically claims a PENDING document (PENDING → PROCESSING). Empty if the document
     * is gone or not pending, so the same document is never processed twice concurrently.
     */
    @Transactional
    public Optional<IngestionSource> beginIngestion(UUID workspaceId, UUID documentId) {
        if (repository.claimForIngestion(documentId, workspaceId, Instant.now()) == 0) {
            return Optional.empty();
        }
        return repository.findByIdAndWorkspaceId(documentId, workspaceId)
                .map(d -> new IngestionSource(workspaceId, documentId, d.getMediaType()));
    }

    /** Opens the stored original. The caller closes the stream. */
    public InputStream openOriginal(UUID workspaceId, UUID documentId) throws IOException {
        String key = transactions.execute(status -> find(workspaceId, documentId).getStorageKey());
        if (key == null) {
            throw new IOException("Document has no stored file");
        }
        return storage.open(key);
    }

    @Transactional
    public void completeIngestion(UUID workspaceId, UUID documentId, Integer pageCount, int chunkCount) {
        repository.findByIdAndWorkspaceId(documentId, workspaceId)
                .filter(d -> d.getStatus() == DocumentStatus.PROCESSING)
                .ifPresent(d -> {
                    d.completeIngestion(pageCount, chunkCount);
                    repository.saveAndFlush(d);
                });
    }

    @Transactional
    public void failIngestion(UUID workspaceId, UUID documentId, String reason) {
        String detail = reason.length() > MAX_STATUS_DETAIL ? reason.substring(0, MAX_STATUS_DETAIL) : reason;
        repository.findByIdAndWorkspaceId(documentId, workspaceId)
                .filter(d -> d.getStatus().isIngestionInFlight())
                .ifPresent(d -> {
                    d.failIngestion(detail);
                    repository.saveAndFlush(d);
                });
    }

    /**
     * System-level recovery after a restart: work that was PROCESSING was interrupted and
     * is returned to PENDING. Returns every document now waiting for ingestion.
     */
    @Transactional
    public List<DocumentIngestionRequested> recoverUnfinishedIngestion() {
        return repository.findByStatusIn(EnumSet.of(DocumentStatus.PENDING, DocumentStatus.PROCESSING))
                .stream()
                .map(d -> {
                    if (d.getStatus() == DocumentStatus.PROCESSING) {
                        d.queueIngestion();
                        repository.saveAndFlush(d);
                    }
                    return new DocumentIngestionRequested(d.getWorkspaceId(), d.getId());
                })
                .toList();
    }

    // --- search-index lifecycle (reported by the indexing process) ---

    /** User-requested (re)indexing of a READY document. 409 if not READY or already indexing. */
    @Transactional
    public DocumentResponse requestIndexing(UUID workspaceId, UUID documentId) {
        Document document = find(workspaceId, documentId);
        if (document.getStatus() != DocumentStatus.READY) {
            throw new ConflictException("Document " + documentId + " has no extracted text to index yet");
        }
        if (document.getIndexStatus() == SearchIndexStatus.INDEXING) {
            throw new ConflictException("Document " + documentId + " is already being indexed");
        }
        document.setIndex(SearchIndexStatus.PENDING, null);
        repository.saveAndFlush(document);
        events.publishEvent(new DocumentIndexingRequested(workspaceId, documentId));
        return DocumentResponse.from(document);
    }

    /** Atomically claims a READY document whose index is PENDING (→ INDEXING). */
    @Transactional
    public boolean beginIndexing(UUID workspaceId, UUID documentId) {
        return repository.claimForIndexing(documentId, workspaceId, Instant.now()) == 1;
    }

    @Transactional
    public void completeIndexing(UUID workspaceId, UUID documentId) {
        transitionIndex(workspaceId, documentId, SearchIndexStatus.INDEXED, null);
    }

    /** Back to PENDING (retried automatically), e.g. while the embedding service is down. */
    @Transactional
    public void deferIndexing(UUID workspaceId, UUID documentId, String reason) {
        transitionIndex(workspaceId, documentId, SearchIndexStatus.PENDING, reason);
    }

    @Transactional
    public void failIndexing(UUID workspaceId, UUID documentId, String reason) {
        transitionIndex(workspaceId, documentId, SearchIndexStatus.FAILED, reason);
    }

    /** System-level: READY documents waiting for embeddings, oldest first. */
    @Transactional(readOnly = true)
    public List<DocumentIndexingRequested> findIndexingBacklog(int limit) {
        return repository.findByIndexStatusOrderByUpdatedAtAsc(SearchIndexStatus.PENDING, PageRequest.of(0, limit))
                .stream()
                .map(d -> new DocumentIndexingRequested(d.getWorkspaceId(), d.getId()))
                .toList();
    }

    /** System-level recovery after a restart: interrupted INDEXING work returns to PENDING. */
    @Transactional
    public int recoverInterruptedIndexing() {
        List<Document> interrupted = repository.findByIndexStatus(SearchIndexStatus.INDEXING);
        interrupted.forEach(d -> {
            d.setIndex(SearchIndexStatus.PENDING, null);
            repository.saveAndFlush(d);
        });
        return interrupted.size();
    }

    /**
     * System-level reconciliation: a READY document whose chunks lack embeddings for the
     * current model (e.g. after a model change) is queued again. No-op while indexing.
     */
    @Transactional
    public void requeueIndexing(UUID workspaceId, UUID documentId) {
        repository.findByIdAndWorkspaceId(documentId, workspaceId)
                .filter(d -> d.getStatus() == DocumentStatus.READY)
                .filter(d -> d.getIndexStatus() != SearchIndexStatus.INDEXING
                        && d.getIndexStatus() != SearchIndexStatus.PENDING)
                .ifPresent(d -> {
                    d.setIndex(SearchIndexStatus.PENDING, null);
                    repository.saveAndFlush(d);
                });
    }

    @Transactional(readOnly = true)
    public SearchCoverage searchCoverage(UUID workspaceId) {
        workspaces.requireExists(workspaceId);
        long indexed = 0, pending = repository.countIngestingInWorkspace(workspaceId), failed = 0;
        for (Object[] row : repository.countIndexStatuses(workspaceId)) {
            long count = (Long) row[1];
            switch (row[0] == null ? SearchIndexStatus.PENDING : (SearchIndexStatus) row[0]) {
                case INDEXED -> indexed += count;
                case FAILED -> failed += count;
                case PENDING, INDEXING -> pending += count;
            }
        }
        return new SearchCoverage(indexed, pending, failed);
    }

    /** Display metadata for search results, restricted to the workspace. */
    public record DocumentSummary(UUID id, String title, String origin, String mediaType) {}

    @Transactional(readOnly = true)
    public Map<UUID, DocumentSummary> summaries(UUID workspaceId, Collection<UUID> documentIds) {
        if (documentIds.isEmpty()) {
            return Map.of();
        }
        return repository.findByWorkspaceIdAndIdIn(workspaceId, documentIds).stream()
                .collect(Collectors.toMap(Document::getId,
                        d -> new DocumentSummary(d.getId(), d.getTitle(), d.getOrigin(), d.getMediaType())));
    }

    private void transitionIndex(UUID workspaceId, UUID documentId, SearchIndexStatus next, String reason) {
        String detail = reason == null || reason.length() <= MAX_STATUS_DETAIL ? reason : reason.substring(0, MAX_STATUS_DETAIL);
        repository.findByIdAndWorkspaceId(documentId, workspaceId)
                .filter(d -> d.getStatus() == DocumentStatus.READY)
                .filter(d -> d.getIndexStatus() == SearchIndexStatus.INDEXING)
                .ifPresent(d -> {
                    d.setIndex(next, detail);
                    repository.saveAndFlush(d);
                });
    }

    // --- helpers ---

    private Document find(UUID workspaceId, UUID documentId) {
        workspaces.requireExists(workspaceId);
        return repository.findByIdAndWorkspaceId(documentId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
    }

    private void deleteStoredFile(String key) {
        try {
            storage.delete(key);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not delete stored file {}; it is now orphaned", key, e);
        }
    }

    private static String normalize(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
