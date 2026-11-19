package com.nexusai.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** Persistence model; internal to the chat module. */
@Entity
@Table(name = "conversations")
class Conversation {

    static final String DEFAULT_TITLE = "New chat";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(nullable = false, length = 200)
    private String title;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Bumped on every new turn so recently active conversations list first. */
    @Column(nullable = false)
    private Instant updatedAt;

    protected Conversation() {
    }

    Conversation(UUID workspaceId, String title) {
        this.workspaceId = workspaceId;
        this.title = title;
        this.updatedAt = Instant.now();
    }

    void rename(String title) {
        this.title = title;
        touch();
    }

    void touch() {
        this.updatedAt = Instant.now();
    }

    boolean hasDefaultTitle() {
        return DEFAULT_TITLE.equals(title);
    }

    UUID getId() { return id; }
    UUID getWorkspaceId() { return workspaceId; }
    String getTitle() { return title; }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }
}
