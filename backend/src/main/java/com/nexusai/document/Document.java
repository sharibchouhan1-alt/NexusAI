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
 * another module.
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

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected Document() {
    }

    Document(UUID workspaceId, String title, DocumentSourceType sourceType,
             String mediaType, String origin) {
        this.workspaceId = workspaceId;
        this.title = title;
        this.sourceType = sourceType;
        this.mediaType = mediaType;
        this.origin = origin;
        this.status = DocumentStatus.REGISTERED;
    }

    void retitle(String title) {
        this.title = title;
    }

    UUID getId() { return id; }
    UUID getWorkspaceId() { return workspaceId; }
    String getTitle() { return title; }
    DocumentSourceType getSourceType() { return sourceType; }
    String getMediaType() { return mediaType; }
    String getOrigin() { return origin; }
    DocumentStatus getStatus() { return status; }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }
}
