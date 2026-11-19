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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** One step of a research plan; internal to the research module. */
@Entity
@Table(name = "research_steps")
class ResearchStep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID runId;

    @Column(nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(nullable = false, updatable = false)
    private int ordinal;

    @Column(nullable = false, updatable = false, length = 300)
    private String title;

    @Column(nullable = false, updatable = false, length = 1000)
    private String goal;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String plannedQueries;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StepStatus status;

    @Column(length = 1000)
    private String statusDetail;

    @Column(nullable = false)
    private int actionCount;

    private Instant startedAt;

    private Instant finishedAt;

    protected ResearchStep() {
    }

    ResearchStep(UUID runId, UUID workspaceId, int ordinal, String title, String goal, List<String> queries) {
        this.runId = runId;
        this.workspaceId = workspaceId;
        this.ordinal = ordinal;
        this.title = title;
        this.goal = goal;
        this.plannedQueries = String.join("\n", queries);
        this.status = StepStatus.PENDING;
    }

    void start() {
        status = StepStatus.RUNNING;
        statusDetail = null;
        startedAt = Instant.now();
        finishedAt = null;
    }

    void finish(StepStatus outcome, String detail, int actions) {
        status = outcome;
        statusDetail = detail == null || detail.length() <= 1000 ? detail : detail.substring(0, 1000);
        actionCount += actions;
        finishedAt = Instant.now();
    }

    /** An interrupted step runs again from the start on resume. */
    void reset() {
        if (status == StepStatus.RUNNING) {
            status = StepStatus.PENDING;
            startedAt = null;
        }
    }

    UUID getId() { return id; }
    UUID getRunId() { return runId; }
    int getOrdinal() { return ordinal; }
    String getTitle() { return title; }
    String getGoal() { return goal; }
    List<String> getPlannedQueries() {
        return plannedQueries.isBlank() ? List.of() : Arrays.stream(plannedQueries.split("\n")).filter(q -> !q.isBlank()).toList();
    }
    StepStatus getStatus() { return status; }
    String getStatusDetail() { return statusDetail; }
    int getActionCount() { return actionCount; }
    Instant getStartedAt() { return startedAt; }
    Instant getFinishedAt() { return finishedAt; }
}
