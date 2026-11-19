package com.nexusai.tool;

/**
 * The audited outcome of one attempted tool call.
 *
 * @param argumentsJson the arguments exactly as proposed (data, never executed)
 * @param result        present only when {@code SUCCEEDED}
 * @param error         user-safe reason when {@code FAILED} or {@code REJECTED}
 */
public record ToolInvocation(String tool, String argumentsJson, Status status, ToolResult result,
                             String error, long durationMs) {

    public enum Status {
        /** The tool ran and returned a result. */
        SUCCEEDED,
        /** The tool ran but its capability failed (e.g. search unavailable). */
        FAILED,
        /** Never ran: unknown tool, invalid arguments, duplicate, or invalid model output. */
        REJECTED
    }

    public static ToolInvocation rejected(String tool, String argumentsJson, String reason) {
        return new ToolInvocation(tool, argumentsJson, Status.REJECTED, null, reason, 0);
    }

    public boolean succeeded() {
        return status == Status.SUCCEEDED;
    }
}
