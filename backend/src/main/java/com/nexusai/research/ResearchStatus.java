package com.nexusai.research;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The research run state machine. Transitions are explicit; anything not listed is illegal.
 *
 * <pre>
 * QUEUED → PLANNING → RUNNING → SYNTHESIZING → COMPLETED
 *                                             ↘ INSUFFICIENT_EVIDENCE
 * (any active state) → FAILED | CANCELLED | QUEUED (interrupted, resumed on restart)
 * FAILED | CANCELLED → QUEUED (resume, keeps plan, finished steps and sources)
 * </pre>
 */
public enum ResearchStatus {
    QUEUED, PLANNING, RUNNING, SYNTHESIZING, COMPLETED, INSUFFICIENT_EVIDENCE, FAILED, CANCELLED;

    private static final Map<ResearchStatus, Set<ResearchStatus>> NEXT = Map.of(
            QUEUED, EnumSet.of(PLANNING, FAILED, CANCELLED),
            PLANNING, EnumSet.of(RUNNING, FAILED, CANCELLED, QUEUED),
            RUNNING, EnumSet.of(SYNTHESIZING, INSUFFICIENT_EVIDENCE, FAILED, CANCELLED, QUEUED),
            SYNTHESIZING, EnumSet.of(COMPLETED, INSUFFICIENT_EVIDENCE, FAILED, CANCELLED, QUEUED),
            COMPLETED, EnumSet.noneOf(ResearchStatus.class),
            INSUFFICIENT_EVIDENCE, EnumSet.noneOf(ResearchStatus.class),
            FAILED, EnumSet.of(QUEUED),
            CANCELLED, EnumSet.of(QUEUED));

    public boolean canMoveTo(ResearchStatus next) {
        return NEXT.get(this).contains(next);
    }

    /** Work is queued or in progress. */
    public boolean isActive() {
        return this == QUEUED || this == PLANNING || this == RUNNING || this == SYNTHESIZING;
    }

    public boolean isTerminal() {
        return !isActive();
    }

    /** A stopped run that can continue where it left off. */
    public boolean isResumable() {
        return this == FAILED || this == CANCELLED;
    }

    static final Set<ResearchStatus> ACTIVE = EnumSet.of(QUEUED, PLANNING, RUNNING, SYNTHESIZING);
}
