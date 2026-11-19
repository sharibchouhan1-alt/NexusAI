package com.nexusai.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Persistence model; internal to the document module and never serialized to clients.
 * The workspace is held as an id, not a JPA association: the Workspace entity belongs to
 * another module. An uploaded original lives in file storage; only its key is held here.
 */
@Entity
@Table(name = "documents")
class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(nullable = false, length = 500)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private DocumentSourceType sourceType;

    @Column(length = 255)
    private String mediaType;

    @Column(length = 2048)
    private String origin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DocumentStatus status;

    @Column(length = 512, updatable = false)
    private String storageKey;

    @Column(updatable = false)
    private Long fileSizeBytes;

    @Column(name = "content_sha256", length = 64, updatable = false)
    private String contentSha256;

    @Column(length = 1000)
    private String statusDetail;

    private Integer pageCount;

    private Integer chunkCount;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private SearchIndexStatus indexStatus;

    @Column(length = 1000)
    private String indexDetail;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected Document() {
    }

    /** Metadata-only registration (no file). */
    Document(UUID workspaceId, String title, DocumentSourceType sourceType,
             String mediaType, String origin) {
        this.workspaceId = workspaceId;
        this.title = title;
        this.sourceType = sourceType;
        this.mediaType = mediaType;
        this.origin = origin;
        this.status = DocumentStatus.REGISTERED;
    }

    /** An uploaded file, already written to storage, waiting for ingestion. */
    static Document uploaded(UUID workspaceId, String title, String mediaType, String originalFilename,
                             String storageKey, long sizeBytes, String sha256) {
        Document document = new Document(workspaceId, title, DocumentSourceType.FILE, mediaType, originalFilename);
        document.storageKey = storageKey;
        document.fileSizeBytes = sizeBytes;
        document.contentSha256 = sha256;
        document.status = DocumentStatus.PENDING;
        return document;
    }

    void retitle(String title) {
        this.title = title;
    }

    boolean hasFile() {
        return storageKey != null;
    }

    // --- ingestion lifecycle (transitions are validated by DocumentService) ---

    void queueIngestion() {
        status = DocumentStatus.PENDING;
        statusDetail = null;
        clearIndex(); // chunks will be replaced, and their embeddings with them
    }

    void startIngestion() {
        status = DocumentStatus.PROCESSING;
        statusDetail = null;
        clearIndex();
    }

    /** Chunks are stored; the document now waits for embeddings. */
    void completeIngestion(Integer pages, int chunks) {
        status = DocumentStatus.READY;
        statusDetail = null;
        pageCount = pages;
        chunkCount = chunks;
        indexStatus = SearchIndexStatus.PENDING;
        indexDetail = null;
    }

    void failIngestion(String reason) {
        status = DocumentStatus.FAILED;
        statusDetail = reason;
        pageCount = null;
        chunkCount = null;
        clearIndex();
    }

    // --- search-index lifecycle (only for READY documents) ---

    void setIndex(SearchIndexStatus status, String detail) {
        indexStatus = status;
        indexDetail = detail;
    }

    private void clearIndex() {
        indexStatus = null;
        indexDetail = null;
    }

    UUID getId() { return id; }
    UUID getWorkspaceId() { return workspaceId; }
    String getTitle() { return title; }
    DocumentSourceType getSourceType() { return sourceType; }
    String getMediaType() { return mediaType; }
    String getOrigin() { return origin; }
    DocumentStatus getStatus() { return status; }
    String getStorageKey() { return storageKey; }
    Long getFileSizeBytes() { return fileSizeBytes; }
    String getStatusDetail() { return statusDetail; }
    Integer getPageCount() { return pageCount; }
    Integer getChunkCount() { return chunkCount; }
    SearchIndexStatus getIndexStatus() { return indexStatus; }
    String getIndexDetail() { return indexDetail; }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }
}
