package com.nexusai.research;

import com.nexusai.agent.StepTask;
import com.nexusai.citation.CitationDraft;
import com.nexusai.citation.CitationService;
import com.nexusai.platform.web.ConflictException;
import com.nexusai.platform.web.ResourceNotFoundException;
import com.nexusai.tool.Passage;
import com.nexusai.tool.ToolInvocation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every persisted state change of a research run, each in its own short transaction. The
 * orchestrator calls these between (slow) model and tool calls, so no transaction is held
 * across them and partial progress survives any interruption.
 */
@Component
class ResearchStore {

    record RunState(UUID id, UUID workspaceId, String objective, ResearchStatus status, int resumeCount, int toolCallCount) {}

    record StepState(UUID id, int ordinal, String title, String goal, List<String> queries, StepStatus status) {}

    record PlannedStep(String title, String goal, List<String> queries) {}

    private final ResearchRunRepository runs;
    private final ResearchStepRepository steps;
    private final ResearchToolCallRepository toolCalls;
    private final ResearchSourceRepository sources;
    private final CitationService citations;

    ResearchStore(ResearchRunRepository runs, ResearchStepRepository steps, ResearchToolCallRepository toolCalls,
                  ResearchSourceRepository sources, CitationService citations) {
        this.runs = runs;
        this.steps = steps;
        this.toolCalls = toolCalls;
        this.sources = sources;
        this.citations = citations;
    }

    // --- lifecycle ---

    @Transactional
    UUID create(UUID workspaceId, String objective) {
        return runs.saveAndFlush(new ResearchRun(workspaceId, objective)).getId();
    }

    @Transactional
    boolean claim(UUID runId) {
        return runs.claim(runId, Instant.now()) == 1;
    }

    @Transactional(readOnly = true)
    RunState run(UUID runId) {
        ResearchRun r = load(runId);
        return new RunState(r.getId(), r.getWorkspaceId(), r.getObjective(), r.getStatus(), r.getResumeCount(), r.getToolCallCount());
    }

    @Transactional(readOnly = true)
    List<StepState> steps(UUID runId) {
        return steps.findByRunIdOrderByOrdinal(runId).stream()
                .map(s -> new StepState(s.getId(), s.getOrdinal(), s.getTitle(), s.getGoal(), s.getPlannedQueries(), s.getStatus()))
                .toList();
    }

    @Transactional
    List<StepState> savePlan(UUID runId, String origin, String model, List<PlannedStep> plan) {
        ResearchRun run = load(runId);
        if (!steps.findByRunIdOrderByOrdinal(runId).isEmpty()) {
            return steps(runId); // already planned (e.g. resumed concurrently)
        }
        int ordinal = 1;
        for (PlannedStep p : plan) {
            steps.saveAndFlush(new ResearchStep(runId, run.getWorkspaceId(), ordinal++, p.title(), p.goal(), p.queries()));
        }
        run.planned(origin, model);
        runs.saveAndFlush(run);
        return steps(runId);
    }

    @Transactional
    void transition(UUID runId, ResearchStatus next, String detail) {
        ResearchRun run = load(runId);
        if (run.getStatus() != next) {
            run.moveTo(next, detail);
            runs.saveAndFlush(run);
        }
    }

    @Transactional(readOnly = true)
    boolean cancelRequested(UUID runId) {
        return runs.cancelRequested(runId).orElse(true); // a deleted run counts as cancelled
    }

    // --- steps and tool calls ---

    @Transactional
    void startStep(UUID stepId) {
        ResearchStep step = steps.findById(stepId).orElseThrow();
        step.start();
        steps.saveAndFlush(step);
    }

    @Transactional
    void finishStep(UUID stepId, StepStatus outcome, String detail, int actions) {
        ResearchStep step = steps.findById(stepId).orElseThrow();
        step.finish(outcome, detail, actions);
        steps.saveAndFlush(step);
    }

    @Transactional(readOnly = true)
    int nextSeq(UUID runId) {
        return toolCalls.maxSeq(runId) + 1;
    }

    @Transactional
    void recordToolCall(UUID runId, UUID stepId, int seq, ToolInvocation invocation, String reason) {
        ResearchRun run = load(runId);
        toolCalls.saveAndFlush(new ResearchToolCall(runId, stepId, run.getWorkspaceId(), seq, invocation, reason));
        run.countToolCall();
        runs.saveAndFlush(run);
    }

    /** Successful calls a step already made (non-empty only when an interrupted step resumes). */
    @Transactional(readOnly = true)
    List<StepTask.PriorCall> priorCalls(UUID runId, UUID stepId) {
        return toolCalls.findByRunIdAndStepIdAndStatusOrderBySeq(runId, stepId, ToolInvocation.Status.SUCCEEDED)
                .stream().map(c -> new StepTask.PriorCall(c.getTool(), c.getArguments())).toList();
    }

    @Transactional(readOnly = true)
    long succeededToolCalls(UUID runId) {
        return toolCalls.countSucceeded(runId);
    }

    @Transactional(readOnly = true)
    Optional<String> lastToolError(UUID runId) {
        return toolCalls.findFirstByRunIdAndErrorIsNotNullOrderBySeqDesc(runId).map(ResearchToolCall::getError);
    }

    // --- evidence ledger ---

    /** Records a passage once per run; returns its handle, or empty when the run's cap is reached. */
    @Transactional
    Optional<String> recordSource(UUID runId, UUID stepId, int toolCallSeq, Passage passage, int maxSources) {
        Optional<ResearchSource> existing = sources.findByRunIdAndChunkId(runId, passage.chunkId());
        if (existing.isPresent()) {
            return Optional.of(existing.get().getHandle());
        }
        long count = sources.countByRunId(runId);
        if (count >= maxSources) {
            return Optional.empty();
        }
        ResearchRun run = load(runId);
        String handle = "P" + (count + 1);
        sources.saveAndFlush(new ResearchSource(runId, run.getWorkspaceId(), handle, stepId, toolCallSeq, passage));
        run.countSource();
        runs.saveAndFlush(run);
        return Optional.of(handle);
    }

    @Transactional(readOnly = true)
    Optional<UUID> resolveHandle(UUID runId, String handle) {
        return sources.findByRunIdAndHandle(runId, handle).map(ResearchSource::getChunkId);
    }

    @Transactional(readOnly = true)
    List<ResearchSource> sources(UUID runId) {
        List<ResearchSource> all = new ArrayList<>(sources.findByRunId(runId));
        all.sort(Comparator.comparingInt(ResearchSource::handleNumber));
        return all;
    }

    // --- outcomes ---

    @Transactional
    void complete(UUID runId, String title, String text, boolean partial, List<String> gaps, String model,
                  List<CitationDraft> drafts) {
        ResearchRun run = load(runId);
        run.complete(title, text, partial, joinGaps(gaps), model);
        runs.saveAndFlush(run);
        citations.replaceForResearchRun(run.getWorkspaceId(), runId, drafts);
    }

    @Transactional
    void finish(UUID runId, ResearchStatus outcome, String detail, List<String> gaps) {
        ResearchRun run = load(runId);
        if (run.getStatus() == outcome) return;
        run.moveTo(outcome, detail);
        if (gaps != null && !gaps.isEmpty()) {
            run.recordGaps(joinGaps(gaps));
        }
        runs.saveAndFlush(run);
    }

    // --- user actions ---

    /** QUEUED runs are cancelled at once; running ones stop at their next checkpoint. */
    @Transactional
    void requestCancel(UUID workspaceId, UUID runId) {
        ResearchRun run = runs.findByIdAndWorkspaceId(runId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Research run", runId));
        if (!run.getStatus().isActive()) {
            throw new ConflictException("Research run " + runId + " is not running");
        }
        run.requestCancel();
        if (run.getStatus() == ResearchStatus.QUEUED) {
            run.moveTo(ResearchStatus.CANCELLED, "Cancelled before it started.");
        }
        runs.saveAndFlush(run);
    }

    /** FAILED/CANCELLED → QUEUED, keeping the plan, completed steps, tool trail and sources. */
    @Transactional
    void resume(UUID workspaceId, UUID runId, int maxResumes) {
        ResearchRun run = runs.findByIdAndWorkspaceId(runId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Research run", runId));
        if (!run.getStatus().isResumable()) {
            throw new ConflictException("Only a failed or cancelled research run can be resumed");
        }
        if (run.getResumeCount() >= maxResumes) {
            throw new ConflictException("This research run was already resumed " + maxResumes + " times; start a new run");
        }
        run.resume("Resumed by the user.");
        runs.saveAndFlush(run);
        steps.findByRunIdOrderByOrdinal(runId).forEach(s -> {
            s.reset();
            steps.saveAndFlush(s);
        });
    }

    @Transactional
    void delete(UUID workspaceId, UUID runId) {
        ResearchRun run = runs.findByIdAndWorkspaceId(runId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Research run", runId));
        if (run.getStatus().isActive()) {
            throw new ConflictException("Cancel the research run before deleting it");
        }
        runs.delete(run); // steps, tool calls, sources and citations cascade in the database
    }

    /**
     * Startup recovery: runs left active by a stopped server are re-queued (bounded by
     * {@code maxResumes}) or failed. Returns the runs to submit.
     */
    @Transactional
    List<UUID> recoverInterrupted(int maxResumes) {
        List<UUID> toSubmit = new ArrayList<>();
        for (ResearchRun run : runs.findByStatusIn(ResearchStatus.ACTIVE)) {
            if (run.isCancelRequested()) {
                run.moveTo(ResearchStatus.CANCELLED, "Cancelled while the server was stopping.");
            } else if (run.getStatus() == ResearchStatus.QUEUED) {
                toSubmit.add(run.getId());
                continue;
            } else if (run.getResumeCount() < maxResumes) {
                run.resume("Interrupted by a server restart; resuming.");
                steps.findByRunIdOrderByOrdinal(run.getId()).forEach(s -> {
                    s.reset();
                    steps.saveAndFlush(s);
                });
                toSubmit.add(run.getId());
            } else {
                run.moveTo(ResearchStatus.FAILED, "Interrupted by a server restart too many times.");
            }
            runs.saveAndFlush(run);
        }
        return toSubmit;
    }

    private ResearchRun load(UUID runId) {
        return runs.findById(runId).orElseThrow(() -> new ResourceNotFoundException("Research run", runId));
    }

    private static String joinGaps(List<String> gaps) {
        return gaps == null || gaps.isEmpty() ? null : String.join("\n", gaps);
    }
}
