package com.nexusai.research;

import com.nexusai.citation.CitationView;
import com.nexusai.tool.ToolInvocation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request and response bodies of the research API. */
public final class ResearchDtos {

    private ResearchDtos() {
    }

    public record StartResearchRequest(@NotBlank @Size(min = 10, max = 2000) String objective) {}

    public record RunSummary(UUID id, UUID workspaceId, String objective, ResearchStatus status, String statusDetail,
                             boolean cancelRequested, int toolCallCount, int sourceCount, String reportTitle,
                             Instant createdAt, Instant startedAt, Instant finishedAt) {}

    /**
     * @param planOrigin MODEL (planned by the model) or FALLBACK (the model's plan was unusable)
     * @param report     present only when {@code status = COMPLETED}
     */
    public record RunDetail(UUID id, UUID workspaceId, String objective, ResearchStatus status, String statusDetail,
                            boolean cancelRequested, int resumeCount, String planOrigin, String model,
                            int toolCallCount, int sourceCount, Instant createdAt, Instant startedAt, Instant finishedAt,
                            List<StepView> steps, List<SourceView> sources, Report report, List<String> gaps,
                            Limits limits) {}

    public record StepView(UUID id, int ordinal, String title, String goal, List<String> plannedQueries,
                           StepStatus status, String statusDetail, int actionCount, Instant startedAt,
                           Instant finishedAt, List<ToolCallView> toolCalls) {}

    /** @param arguments the arguments as the model proposed them (JSON; data, never executed) */
    public record ToolCallView(int seq, String tool, String arguments, String reason, ToolInvocation.Status status,
                               String resultSummary, String error, int durationMs, Instant createdAt) {}

    /** A gathered passage with provenance; {@code handle} is how the agent referred to it. */
    public record SourceView(String handle, UUID documentId, UUID chunkId, String documentTitle, Integer pageNumber,
                             int chunkIndex, int charStart, int charEnd, Double score, int stepOrdinal,
                             int toolCallSeq, String content) {}

    /**
     * @param text     verified report: summary paragraph(s), then "## Heading" sections, with [n] markers
     * @param partial  the evidence covers the objective only partly (see gaps)
     */
    public record Report(String title, String text, boolean partial, List<CitationView> citations) {}

    public record Limits(int maxSteps, int maxActionsPerStep, int maxToolCalls, int maxSources, long maxDurationSeconds) {}
}
