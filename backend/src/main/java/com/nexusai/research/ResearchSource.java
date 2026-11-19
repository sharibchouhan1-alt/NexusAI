package com.nexusai.research;

import com.nexusai.tool.Passage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** A passage gathered during a run, with provenance and a text snapshot; internal to research. */
@Entity
@Table(name = "research_sources")
class ResearchSource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID runId;

    @Column(nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(nullable = false, updatable = false, length = 16)
    private String handle;

    @Column(nullable = false, updatable = false)
    private UUID stepId;

    @Column(nullable = false, updatable = false)
    private int toolCallSeq;

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
    private String content;

    @Column(updatable = false)
    private Double score;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected ResearchSource() {
    }

    ResearchSource(UUID runId, UUID workspaceId, String handle, UUID stepId, int toolCallSeq, Passage p) {
        this.runId = runId;
        this.workspaceId = workspaceId;
        this.handle = handle;
        this.stepId = stepId;
        this.toolCallSeq = toolCallSeq;
        this.documentId = p.documentId();
        this.chunkId = p.chunkId();
        this.documentTitle = p.documentTitle().length() <= 500 ? p.documentTitle() : p.documentTitle().substring(0, 500);
        this.pageNumber = p.pageNumber();
        this.chunkIndex = p.chunkIndex();
        this.charStart = p.charStart();
        this.charEnd = p.charEnd();
        this.content = p.content();
        this.score = p.score();
    }

    int handleNumber() {
        return Integer.parseInt(handle.substring(1));
    }

    String getHandle() { return handle; }
    UUID getStepId() { return stepId; }
    int getToolCallSeq() { return toolCallSeq; }
    UUID getDocumentId() { return documentId; }
    UUID getChunkId() { return chunkId; }
    String getDocumentTitle() { return documentTitle; }
    Integer getPageNumber() { return pageNumber; }
    int getChunkIndex() { return chunkIndex; }
    int getCharStart() { return charStart; }
    int getCharEnd() { return charEnd; }
    String getContent() { return content; }
    Double getScore() { return score; }
}
