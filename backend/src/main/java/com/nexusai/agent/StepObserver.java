package com.nexusai.agent;

import com.nexusai.tool.ToolInvocation;

/**
 * Observability hook: called for every attempted tool call (including rejected and fallback
 * calls) right after it happens, so the caller can persist the trail as it is produced.
 */
@FunctionalInterface
public interface StepObserver {

    /** @param reason the model's stated reason, or the rule that triggered a fallback call */
    void onToolCall(ToolInvocation invocation, String reason);
}
