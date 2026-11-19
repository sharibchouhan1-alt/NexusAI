package com.nexusai.research;

import com.nexusai.citation.CitationService;
import com.nexusai.platform.web.PageResponse;
import com.nexusai.platform.web.ResourceNotFoundException;
import com.nexusai.research.ResearchDtos.Limits;
import com.nexusai.research.ResearchDtos.Report;
import com.nexusai.research.ResearchDtos.RunDetail;
import com.nexusai.research.ResearchDtos.RunSummary;
import com.nexusai.research.ResearchDtos.SourceView;
import com.nexusai.research.ResearchDtos.StepView;
import com.nexusai.research.ResearchDtos.ToolCallView;
import com.nexusai.workspace.WorkspaceService;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Published interface of the research module: workspace-scoped research runs. Starting,
 * resuming and cancelling return immediately; execution happens in the background
 * (see {@link ResearchOrchestrator}) and progress is read with {@link #get}.
 */
@Service
public class ResearchService {

    private final WorkspaceService workspaces;
    private final ResearchStore store;
    private final ResearchOrchestrator orchestrator;
    private final ResearchRunRepository runs;
    private final ResearchStepRepository steps;
    private final ResearchToolCallRepository toolCalls;
    private final ResearchSourceRepository sources;
    private final CitationService citations;
    private final ResearchProperties properties;

    ResearchService(WorkspaceService workspaces, ResearchStore store, ResearchOrchestrator orchestrator,
                    ResearchRunRepository runs, ResearchStepRepository steps, ResearchToolCallRepository toolCalls,
                    ResearchSourceRepository sources, CitationService citations, ResearchProperties properties) {
        this.workspaces = workspaces;
        this.store = store;
        this.orchestrator = orchestrator;
        this.runs = runs;
        this.steps = steps;
        this.toolCalls = toolCalls;
        this.sources = sources;
        this.citations = citations;
        this.properties = properties;
    }

    public RunDetail start(UUID workspaceId, String objective) {
        workspaces.requireExists(workspaceId);
        UUID runId = store.create(workspaceId, objective.strip()); // committed before the worker sees it
        orchestrator.submit(runId);
        return get(workspaceId, runId);
    }

    @Transactional(readOnly = true)
    public PageResponse<RunSummary> list(UUID workspaceId, int page, int size) {
        workspaces.requireExists(workspaceId);
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        return PageResponse.of(runs.findByWorkspaceId(workspaceId, pageable), ResearchService::summary);
    }

    @Transactional(readOnly = true)
    public RunDetail get(UUID workspaceId, UUID runId) {
        ResearchRun run = find(workspaceId, runId);
        List<ResearchStep> plan = steps.findByRunIdOrderByOrdinal(runId);
        Map<UUID, Integer> ordinalOf = plan.stream().collect(Collectors.toMap(ResearchStep::getId, ResearchStep::getOrdinal));
        Map<UUID, List<ToolCallView>> callsByStep = toolCalls.findByRunIdOrderBySeq(runId).stream()
                .collect(Collectors.groupingBy(ResearchToolCall::getStepId, Collectors.mapping(c -> new ToolCallView(
                        c.getSeq(), c.getTool(), c.getArguments(), c.getReason(), c.getStatus(), c.getResultSummary(),
                        c.getError(), c.getDurationMs(), c.getCreatedAt()), Collectors.toList())));

        List<StepView> stepViews = plan.stream().map(s -> new StepView(s.getId(), s.getOrdinal(), s.getTitle(), s.getGoal(),
                s.getPlannedQueries(), s.getStatus(), s.getStatusDetail(), s.getActionCount(), s.getStartedAt(),
                s.getFinishedAt(), callsByStep.getOrDefault(s.getId(), List.of()))).toList();

        List<SourceView> sourceViews = sources.findByRunId(runId).stream()
                .sorted(Comparator.comparingInt(ResearchSource::handleNumber))
                .map(s -> new SourceView(s.getHandle(), s.getDocumentId(), s.getChunkId(), s.getDocumentTitle(),
                        s.getPageNumber(), s.getChunkIndex(), s.getCharStart(), s.getCharEnd(), s.getScore(),
                        ordinalOf.getOrDefault(s.getStepId(), 0), s.getToolCallSeq(), s.getContent()))
                .toList();

        Report report = run.getStatus() == ResearchStatus.COMPLETED && run.getReportText() != null
                ? new Report(run.getReportTitle(), run.getReportText(), Boolean.TRUE.equals(run.getReportPartial()),
                        citations.forResearchRun(workspaceId, runId))
                : null;
        List<String> gaps = run.getReportGaps() == null ? List.of()
                : Arrays.stream(run.getReportGaps().split("\n")).filter(g -> !g.isBlank()).toList();

        return new RunDetail(run.getId(), run.getWorkspaceId(), run.getObjective(), run.getStatus(), run.getStatusDetail(),
                run.isCancelRequested(), run.getResumeCount(), run.getPlanOrigin(), run.getModel(), run.getToolCallCount(),
                run.getSourceCount(), run.getCreatedAt(), run.getStartedAt(), run.getFinishedAt(), stepViews, sourceViews,
                report, gaps, limits());
    }

    public RunDetail cancel(UUID workspaceId, UUID runId) {
        workspaces.requireExists(workspaceId);
        store.requestCancel(workspaceId, runId);
        return get(workspaceId, runId);
    }

    public RunDetail resume(UUID workspaceId, UUID runId) {
        workspaces.requireExists(workspaceId);
        store.resume(workspaceId, runId, properties.maxResumes());
        orchestrator.submit(runId);
        return get(workspaceId, runId);
    }

    public void delete(UUID workspaceId, UUID runId) {
        workspaces.requireExists(workspaceId);
        store.delete(workspaceId, runId);
    }

    private ResearchRun find(UUID workspaceId, UUID runId) {
        workspaces.requireExists(workspaceId);
        return runs.findByIdAndWorkspaceId(runId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Research run", runId));
    }

    private Limits limits() {
        return new Limits(properties.maxSteps(), properties.maxActionsPerStep(), properties.maxToolCalls(),
                properties.maxSources(), properties.maxDuration().toSeconds());
    }

    private static RunSummary summary(ResearchRun r) {
        return new RunSummary(r.getId(), r.getWorkspaceId(), r.getObjective(), r.getStatus(), r.getStatusDetail(),
                r.isCancelRequested(), r.getToolCallCount(), r.getSourceCount(), r.getReportTitle(), r.getCreatedAt(),
                r.getStartedAt(), r.getFinishedAt());
    }
}
