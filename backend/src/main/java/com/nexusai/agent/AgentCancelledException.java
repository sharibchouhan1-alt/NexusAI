package com.nexusai.agent;

/** The run was cancelled; the agent stopped before its next action. */
public class AgentCancelledException extends RuntimeException {

    public AgentCancelledException() {
        super("Cancelled");
    }
}
