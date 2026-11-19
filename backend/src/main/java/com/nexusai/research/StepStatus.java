package com.nexusai.research;

/** Lifecycle of one plan step. */
public enum StepStatus {
    PENDING,
    RUNNING,
    /** At least one tool call succeeded. */
    COMPLETED,
    /** Every tool call of the step failed or was rejected. */
    FAILED,
    /** Not executed (the run's tool budget was exhausted). */
    SKIPPED
}
