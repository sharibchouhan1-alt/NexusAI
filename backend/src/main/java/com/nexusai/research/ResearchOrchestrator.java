package com.nexusai.research;

import com.nexusai.agent.AgentCancelledException;
import com.nexusai.agent.AgentLimits;
import com.nexusai.agent.StepAgent;
import com.nexusai.agent.StepOutcome;
import com.nexusai.agent.StepTask;
import com.nexusai.platform.ai.GenerationService;
import com.nexusai.platform.ai.GenerationUnavailableException;
import com.nexusai.research.ResearchStore.RunState;
import com.nexusai.research.ResearchStore.StepState;
import com.nexusai.tool.EvidenceLedger;
import com.nexusai.tool.Passage;
import com.nexusai.tool.ToolContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

/**
 * Executes a research run as an explicit state machine:
 * QUEUED → PLANNING → RUNNING (one bounded agent loop per step) → SYNTHESIZING → outcome.
 *
 * <p>Runs on a bounded in-process worker pool. Cancellation and the wall-clock limit are
 * checked before every step and every agent action. Every state change is persisted, so an
 * interrupted run resumes where it stopped (plan, finished steps and sources are kept).
 */
@Component
class ResearchOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ResearchOrchestrator.class);

    private final ResearchStore store;
    private final ResearchPlanner planner;
    private final ResearchSynthesizer synthesizer;
    private final StepAgent agent;
    private final GenerationService generation;
    private final ResearchProperties properties;
    private final TaskExecutor executor;

    ResearchOrchestrator(ResearchStore store, ResearchPlanner planner, ResearchSynthesizer synthesizer, StepAgent agent,
                         GenerationService generation, ResearchProperties properties,
                         @Qualifier(ResearchConfig.EXECUTOR) TaskExecutor executor) {
        this.store = store;
        this.planner = planner;
        this.synthesizer = synthesizer;
        this.agent = agent;
        this.generation = generation;
        this.properties = properties;
        this.executor = executor;
    }

    void submit(UUID runId) {
        try {
            executor.execute(() -> execute(runId));
        } catch (TaskRejectedException e) {
            log.warn("Research queue full; run {} failed", runId);
            store.finish(runId, ResearchStatus.FAILED, "The server is busy with other research. Resume this run later.", null);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    void recoverInterruptedRuns() {
        List<UUID> runs = store.recoverInterrupted(properties.maxResumes());
        if (!runs.isEmpty()) {
            log.info("Resuming {} research run(s) left unfinished", runs.size());
        }
        runs.forEach(this::submit);
    }

    /** Signals that a run must stop: cancellation, or its wall-clock budget ran out. */
    private static final class Stop extends RuntimeException {
        final boolean timedOut;

        Stop(boolean timedOut) {
            super(timedOut ? "timeout" : "cancelled", null, false, false);
            this.timedOut = timedOut;
        }
    }

    void execute(UUID runId) {
        if (!store.claim(runId)) {
            return; // cancelled, deleted, or already being executed
        }
        long deadline = System.nanoTime() + properties.maxDuration().toNanos();
        try {
            RunState run = store.run(runId);
            List<StepState> steps = store.steps(runId);

            // PLANNING (skipped on resume: the persisted plan is reused)
            if (steps.isEmpty()) {
                checkpoint(runId, deadline);
                ResearchPlanner.Plan plan = planner.plan(run.objective());
                steps = store.savePlan(runId, plan.origin(), generation.modelId(), plan.steps());
            }
            store.transition(runId, ResearchStatus.RUNNING, null);

            // RUNNING: one bounded agent loop per unfinished step
            RunLedger ledger = new RunLedger(runId, store.nextSeq(runId));
            ToolContext tools = new ToolContext(run.workspaceId(), ledger);
            int remaining = Math.max(0, properties.maxToolCalls() - store.run(runId).toolCallCount());
            for (StepState step : steps) {
                if (step.status() != StepStatus.PENDING && step.status() != StepStatus.RUNNING) continue;
                checkpoint(runId, deadline);
                if (remaining <= 0) {
                    store.finishStep(step.id(), StepStatus.SKIPPED, "The run's tool-call budget is exhausted.", 0);
                    continue;
                }
                store.startStep(step.id());
                ledger.stepId = step.id();
                StepTask task = new StepTask(run.objective(), step.ordinal(), steps.size(), step.title(), step.goal(), step.queries(),
                        store.priorCalls(runId, step.id()));
                StepOutcome outcome;
                try {
                    outcome = agent.run(task, tools, new AgentLimits(properties.maxActionsPerStep(), remaining),
                            (invocation, reason) -> store.recordToolCall(runId, step.id(), ledger.seq++, invocation, reason),
                            () -> stopRequested(runId, deadline));
                } catch (AgentCancelledException e) {
                    throw new Stop(System.nanoTime() > deadline);
                }
                remaining -= outcome.toolCalls();
                store.finishStep(step.id(), outcome.succeeded() > 0 ? StepStatus.COMPLETED : StepStatus.FAILED,
                        describe(outcome), outcome.actions());
            }
            checkpoint(runId, deadline);

            // Evidence gate
            List<ResearchSource> sources = store.sources(runId);
            if (sources.isEmpty()) {
                if (store.succeededToolCalls(runId) == 0) {
                    store.finish(runId, ResearchStatus.FAILED, "The research tools failed: "
                            + store.lastToolError(runId).orElse("no tool call succeeded") + " Resume the run once this is fixed.", null);
                } else {
                    store.finish(runId, ResearchStatus.INSUFFICIENT_EVIDENCE,
                            "The searches found no passages in this workspace's documents.", null);
                }
                return;
            }

            // SYNTHESIZING
            store.transition(runId, ResearchStatus.SYNTHESIZING, null);
            ResearchSynthesizer.Outcome report = synthesizer.synthesize(run.objective(),
                    store.steps(runId).stream().map(StepState::title).toList(), sources);
            checkpoint(runId, deadline);
            if (report.grounded()) {
                store.complete(runId, report.title(), report.text(), report.partial(), report.gaps(),
                        generation.modelId(), report.citations());
            } else {
                store.finish(runId, ResearchStatus.INSUFFICIENT_EVIDENCE, report.detail(), report.gaps());
            }
        } catch (Stop stop) {
            if (stop.timedOut) {
                store.finish(runId, ResearchStatus.FAILED, "The run exceeded its time limit of "
                        + properties.maxDuration().toMinutes() + " minutes. Resume it to continue.", null);
            } else {
                store.finish(runId, ResearchStatus.CANCELLED, "Cancelled by the user.", null);
            }
        } catch (GenerationUnavailableException e) {
            store.finish(runId, ResearchStatus.FAILED, e.getMessage() + " Resume the run once the model is available.", null);
        } catch (RuntimeException e) {
            log.error("Research run {} failed unexpectedly", runId, e);
            safeFail(runId);
        }
    }

    private void checkpoint(UUID runId, long deadline) {
        if (store.cancelRequested(runId)) throw new Stop(false);
        if (System.nanoTime() > deadline) throw new Stop(true);
    }

    private boolean stopRequested(UUID runId, long deadline) {
        return store.cancelRequested(runId) || System.nanoTime() > deadline;
    }

    private void safeFail(UUID runId) {
        try {
            store.finish(runId, ResearchStatus.FAILED, "An unexpected error occurred. Resume the run to try again.", null);
        } catch (RuntimeException ignored) {
            // the run may have been deleted
        }
    }

    private static String describe(StepOutcome o) {
        String base = switch (o.reason()) {
            case MODEL_FINISHED -> "The agent finished the step.";
            case ACTION_LIMIT -> "Reached the step's action limit.";
            case BUDGET_EXHAUSTED -> "The run's tool-call budget was exhausted.";
            case FALLBACK -> "Searched the planned queries (the model gave no usable action).";
            case NO_NEW_ACTIONS -> "Ended: the agent had nothing new to try.";
            case TOOLS_FAILING -> "The tools kept failing.";
        };
        return o.detail() == null || o.detail().isBlank() ? base : base + " " + o.detail();
    }

    /** The run's evidence ledger: provenance is persisted the moment a tool sees a passage. */
    private final class RunLedger implements EvidenceLedger {
        private final UUID runId;
        volatile UUID stepId;
        int seq; // the sequence number of the tool call in progress

        RunLedger(UUID runId, int firstSeq) {
            this.runId = runId;
            this.seq = firstSeq;
        }

        @Override
        public Optional<String> record(Passage passage) {
            return store.recordSource(runId, stepId, seq, passage, properties.maxSources());
        }

        @Override
        public Optional<UUID> resolve(String handle) {
            return store.resolveHandle(runId, handle);
        }
    }
}
