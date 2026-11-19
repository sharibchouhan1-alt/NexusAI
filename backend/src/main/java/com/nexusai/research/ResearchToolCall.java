package com.nexusai.research;

import com.nexusai.tool.ToolInvocation;
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

/** Audit record of one attempted tool call; internal to the research module. */
@Entity
@Table(name = "research_tool_calls")
class ResearchToolCall {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID runId;

    @Column(nullable = false, updatable = false)
    private UUID stepId;

    @Column(nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(nullable = false, updatable = false)
    private int seq;

    @Column(nullable = false, updatable = false, length = 64)
    private String tool;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String arguments;

    @Column(updatable = false, length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private ToolInvocation.Status status;

    @Column(updatable = false, length = 2000)
    private String resultSummary;

    @Column(updatable = false, length = 1000)
    private String error;

    @Column(nullable = false, updatable = false)
    private int durationMs;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected ResearchToolCall() {
    }

    ResearchToolCall(UUID runId, UUID stepId, UUID workspaceId, int seq, ToolInvocation inv, String reason) {
        this.runId = runId;
        this.stepId = stepId;
        this.workspaceId = workspaceId;
        this.seq = seq;
        this.tool = cut(inv.tool(), 64);
        this.arguments = inv.argumentsJson() == null ? "{}" : inv.argumentsJson();
        this.reason = cut(reason, 1000);
        this.status = inv.status();
        this.resultSummary = inv.result() == null ? null : cut(inv.result().summary(), 2000);
        this.error = cut(inv.error(), 1000);
        this.durationMs = (int) Math.min(Integer.MAX_VALUE, inv.durationMs());
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    UUID getStepId() { return stepId; }
    int getSeq() { return seq; }
    String getTool() { return tool; }
    String getArguments() { return arguments; }
    String getReason() { return reason; }
    ToolInvocation.Status getStatus() { return status; }
    String getResultSummary() { return resultSummary; }
    String getError() { return error; }
    int getDurationMs() { return durationMs; }
    Instant getCreatedAt() { return createdAt; }
}
