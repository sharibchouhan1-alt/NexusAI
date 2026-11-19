package com.nexusai.agent;

/**
 * Hard bounds for one step. The loop always terminates: at most {@code maxActions} model
 * decisions, at most {@code remainingToolCalls} tool invocations (shared across the run).
 */
public record AgentLimits(int maxActions, int remainingToolCalls) {

    public AgentLimits {
        if (maxActions < 1) throw new IllegalArgumentException("maxActions must be >= 1");
        if (remainingToolCalls < 0) throw new IllegalArgumentException("remainingToolCalls must be >= 0");
    }
}
