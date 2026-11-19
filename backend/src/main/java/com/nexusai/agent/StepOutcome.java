package com.nexusai.agent;

/**
 * How a step ended.
 *
 * @param actions   model decisions made (bounded by {@link AgentLimits#maxActions()})
 * @param toolCalls tool invocations attempted, including rejected ones
 * @param succeeded tool invocations that succeeded
 */
public record StepOutcome(FinishReason reason, int actions, int toolCalls, int succeeded, String detail) {

    public enum FinishReason {
        /** The model decided the step's goal was covered. */
        MODEL_FINISHED,
        /** The per-step action bound was reached. */
        ACTION_LIMIT,
        /** The run-wide tool-call budget was exhausted. */
        BUDGET_EXHAUSTED,
        /** The model gave no usable actions; the planned queries were searched instead. */
        FALLBACK,
        /** The model kept proposing calls it had already made: nothing new to do in this step. */
        NO_NEW_ACTIONS,
        /** Tools kept failing (e.g. search unavailable). */
        TOOLS_FAILING
    }
}
