package com.nexusai.chat;

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

/** Persistence model; internal to the chat module. */
@Entity
@Table(name = "messages")
class ChatMessage {

    enum Role { USER, ASSISTANT }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID conversationId;

    @Column(nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(nullable = false, updatable = false)
    private int seq;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private Role role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MessageStatus status;

    @Column(length = 1000)
    private String statusDetail;

    @Column(length = 200)
    private String model;

    private Integer retrievedCount;

    private Integer latencyMs;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected ChatMessage() {
    }

    private ChatMessage(UUID workspaceId, UUID conversationId, int seq, Role role, String content, MessageStatus status) {
        this.workspaceId = workspaceId;
        this.conversationId = conversationId;
        this.seq = seq;
        this.role = role;
        this.content = content;
        this.status = status;
    }

    static ChatMessage question(UUID workspaceId, UUID conversationId, int seq, String text) {
        return new ChatMessage(workspaceId, conversationId, seq, Role.USER, text, MessageStatus.COMPLETE);
    }

    /** Placeholder persisted before generation starts, so the turn exists even if the server stops. */
    static ChatMessage pendingAnswer(UUID workspaceId, UUID conversationId, int seq) {
        return new ChatMessage(workspaceId, conversationId, seq, Role.ASSISTANT, "", MessageStatus.PENDING);
    }

    void restartGeneration() {
        status = MessageStatus.PENDING;
        statusDetail = null;
        content = "";
    }

    void complete(MessageStatus outcome, String text, String detail, String model, int retrieved, long latencyMs) {
        this.status = outcome;
        this.content = text == null ? "" : text;
        this.statusDetail = detail == null || detail.length() <= 1000 ? detail : detail.substring(0, 1000);
        this.model = model;
        this.retrievedCount = retrieved;
        this.latencyMs = (int) Math.min(Integer.MAX_VALUE, latencyMs);
    }

    UUID getId() { return id; }
    UUID getConversationId() { return conversationId; }
    int getSeq() { return seq; }
    Role getRole() { return role; }
    String getContent() { return content; }
    MessageStatus getStatus() { return status; }
    String getStatusDetail() { return statusDetail; }
    String getModel() { return model; }
    Integer getRetrievedCount() { return retrievedCount; }
    Integer getLatencyMs() { return latencyMs; }
    Instant getCreatedAt() { return createdAt; }
}
