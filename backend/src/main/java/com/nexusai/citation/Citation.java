package com.nexusai.citation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** Persistence model of a citation; internal to the citation module. */
@Entity
@Table(name = "citations")
class Citation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Owner: a chat answer, or ... */
    @Column(updatable = false)
    private UUID messageId;

    /** ... a research report. Exactly one owner is set (database check). */
    @Column(updatable = false)
    private UUID researchRunId;

    @Column(nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(nullable = false, updatable = false)
    private int ordinal;

    @Column(nullable = false, updatable = false, length = 32)
    private String sourceKind = "DOCUMENT_CHUNK";

    @Column(nullable = false, updatable = false)
    private UUID documentId;

    @Column(nullable = false, updatable = false)
    private UUID chunkId;

    @Column(nullable = false, updatable = false, length = 500)
    private String documentTitle;

    @Column(updatable = false)
    private Integer pageNumber;

    @Column(nullable = false, updatable = false)
    private int chunkIndex;

    @Column(nullable = false, updatable = false)
    private int charStart;

    @Column(nullable = false, updatable = false)
    private int charEnd;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String quote;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String passage;

    @Column(nullable = false, updatable = false)
    private double score;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Citation() {
    }

    static Citation forMessage(UUID workspaceId, UUID messageId, CitationDraft d) {
        Citation c = new Citation(workspaceId, d);
        c.messageId = messageId;
        return c;
    }

    static Citation forResearchRun(UUID workspaceId, UUID researchRunId, CitationDraft d) {
        Citation c = new Citation(workspaceId, d);
        c.researchRunId = researchRunId;
        return c;
    }

    private Citation(UUID workspaceId, CitationDraft d) {
        this.workspaceId = workspaceId;
        this.ordinal = d.ordinal();
        this.documentId = d.documentId();
        this.chunkId = d.chunkId();
        this.documentTitle = d.documentTitle().length() > 500 ? d.documentTitle().substring(0, 500) : d.documentTitle();
        this.pageNumber = d.pageNumber();
        this.chunkIndex = d.chunkIndex();
        this.charStart = d.charStart();
        this.charEnd = d.charEnd();
        this.quote = d.quote();
        this.passage = d.passage();
        this.score = d.score();
    }

    CitationView toView() {
        return new CitationView(ordinal, sourceKind, documentId, chunkId, documentTitle, pageNumber, chunkIndex,
                charStart, charEnd, quote, passage, score);
    }

    UUID getMessageId() { return messageId; }
    int getOrdinal() { return ordinal; }
}
