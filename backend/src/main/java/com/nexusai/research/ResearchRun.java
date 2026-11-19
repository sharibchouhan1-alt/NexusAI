package com.nexusai.research;

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

/** A research session; internal to the research module. Status changes go through {@link #moveTo}. */
@Entity
@Table(name = "research_runs")
class ResearchRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String objective;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ResearchStatus status;

    @Column(length = 1000)
    private String statusDetail;

    @Column(nullable = false)
    private boolean cancelRequested;

    @Column(nullable = false)
    private int resumeCount;

    @Column(length = 16)
    private String planOrigin;

    @Column(length = 200)
    private String model;

    @Column(length = 500)
    private String reportTitle;

    @Column(columnDefinition = "text")
    private String reportText;

    private Boolean reportPartial;

    @Column(columnDefinition = "text")
    private String reportGaps;

    @Column(nullable = false)
    private int toolCallCount;

    @Column(nullable = false)
    private int sourceCount;

    private Instant startedAt;

    private Instant finishedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected ResearchRun() {
    }

    ResearchRun(UUID workspaceId, String objective) {
        this.workspaceId = workspaceId;
        this.objective = objective;
        this.status = ResearchStatus.QUEUED;
    }

    /** The only way to change status: illegal transitions are programming errors. */
    void moveTo(ResearchStatus next, String detail) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Illegal research transition " + status + " -> " + next);
        }
        status = next;
        statusDetail = detail == null || detail.length() <= 1000 ? detail : detail.substring(0, 1000);
        if (next == ResearchStatus.PLANNING && startedAt == null) {
            startedAt = Instant.now();
        }
        finishedAt = next.isTerminal() ? Instant.now() : null;
    }

    void requestCancel() {
        cancelRequested = true;
    }

    /** FAILED/CANCELLED/interrupted → QUEUED, keeping the plan, finished steps and sources. */
    void resume(String detail) {
        moveTo(ResearchStatus.QUEUED, detail);
        cancelRequested = false;
        resumeCount++;
    }

    void planned(String origin, String model) {
        this.planOrigin = origin;
        this.model = model;
    }

    void countToolCall() {
        toolCallCount++;
    }

    void countSource() {
        sourceCount++;
    }

    void complete(String title, String text, boolean partial, String gaps, String model) {
        moveTo(ResearchStatus.COMPLETED, null);
        this.reportTitle = title;
        this.reportText = text;
        this.reportPartial = partial;
        this.reportGaps = gaps;
        this.model = model;
    }

    void recordGaps(String gaps) {
        this.reportGaps = gaps;
    }

    UUID getId() { return id; }
    UUID getWorkspaceId() { return workspaceId; }
    String getObjective() { return objective; }
    ResearchStatus getStatus() { return status; }
    String getStatusDetail() { return statusDetail; }
    boolean isCancelRequested() { return cancelRequested; }
    int getResumeCount() { return resumeCount; }
    String getPlanOrigin() { return planOrigin; }
    String getModel() { return model; }
    String getReportTitle() { return reportTitle; }
    String getReportText() { return reportText; }
    Boolean getReportPartial() { return reportPartial; }
    String getReportGaps() { return reportGaps; }
    int getToolCallCount() { return toolCallCount; }
    int getSourceCount() { return sourceCount; }
    Instant getStartedAt() { return startedAt; }
    Instant getFinishedAt() { return finishedAt; }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }
}
