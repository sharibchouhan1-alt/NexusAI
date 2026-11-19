package com.nexusai.agent;

import java.util.List;

/**
 * One step of a research plan, as the agent works on it.
 *
 * @param priorCalls successful calls this step already made before the run was interrupted;
 *                   the agent will not repeat them after a resume
 */
public record StepTask(String objective, int ordinal, int stepCount, String title, String goal,
                       List<String> plannedQueries, List<PriorCall> priorCalls) {

    /** A tool call persisted earlier for this step: tool name and its JSON arguments. */
    public record PriorCall(String tool, String argumentsJson) {}

    public StepTask {
        plannedQueries = plannedQueries == null ? List.of() : List.copyOf(plannedQueries);
        priorCalls = priorCalls == null ? List.of() : List.copyOf(priorCalls);
    }

    public StepTask(String objective, int ordinal, int stepCount, String title, String goal, List<String> plannedQueries) {
        this(objective, ordinal, stepCount, title, goal, plannedQueries, List.of());
    }
}
