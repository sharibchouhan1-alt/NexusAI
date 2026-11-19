package com.nexusai.research;

import static com.nexusai.research.ResearchStatus.CANCELLED;
import static com.nexusai.research.ResearchStatus.COMPLETED;
import static com.nexusai.research.ResearchStatus.FAILED;
import static com.nexusai.research.ResearchStatus.INSUFFICIENT_EVIDENCE;
import static com.nexusai.research.ResearchStatus.PLANNING;
import static com.nexusai.research.ResearchStatus.QUEUED;
import static com.nexusai.research.ResearchStatus.RUNNING;
import static com.nexusai.research.ResearchStatus.SYNTHESIZING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResearchStatusTest {

    @Test
    void happyPathTransitionsAreAllowed() {
        assertThat(QUEUED.canMoveTo(PLANNING)).isTrue();
        assertThat(PLANNING.canMoveTo(RUNNING)).isTrue();
        assertThat(RUNNING.canMoveTo(SYNTHESIZING)).isTrue();
        assertThat(SYNTHESIZING.canMoveTo(COMPLETED)).isTrue();
        assertThat(SYNTHESIZING.canMoveTo(INSUFFICIENT_EVIDENCE)).isTrue();
        assertThat(RUNNING.canMoveTo(INSUFFICIENT_EVIDENCE)).isTrue();
    }

    @Test
    void skippingStagesAndLeavingFinalOutcomesIsIllegal() {
        assertThat(QUEUED.canMoveTo(RUNNING)).isFalse();
        assertThat(QUEUED.canMoveTo(COMPLETED)).isFalse();
        assertThat(PLANNING.canMoveTo(COMPLETED)).isFalse();
        assertThat(RUNNING.canMoveTo(COMPLETED)).isFalse();
        for (ResearchStatus next : ResearchStatus.values()) {
            assertThat(COMPLETED.canMoveTo(next)).as("COMPLETED -> " + next).isFalse();
            assertThat(INSUFFICIENT_EVIDENCE.canMoveTo(next)).as("INSUFFICIENT_EVIDENCE -> " + next).isFalse();
        }
    }

    @Test
    void activeStatesCanStopAndStoppedRunsCanResume() {
        for (ResearchStatus active : ResearchStatus.ACTIVE) {
            assertThat(active.canMoveTo(FAILED)).isTrue();
            assertThat(active.canMoveTo(CANCELLED)).isTrue();
            assertThat(active.isActive()).isTrue();
        }
        assertThat(FAILED.canMoveTo(QUEUED)).isTrue();
        assertThat(CANCELLED.canMoveTo(QUEUED)).isTrue();
        assertThat(FAILED.isResumable()).isTrue();
        assertThat(COMPLETED.isResumable()).isFalse();
    }

    @Test
    void entityRefusesIllegalTransitionsAndTracksTiming() {
        ResearchRun run = new ResearchRun(UUID.randomUUID(), "Compare the two reports");
        assertThatThrownBy(() -> run.moveTo(COMPLETED, null)).isInstanceOf(IllegalStateException.class);
        run.moveTo(PLANNING, null);
        assertThat(run.getStartedAt()).isNotNull();
        run.moveTo(FAILED, "model down");
        assertThat(run.getFinishedAt()).isNotNull();
        run.resume("again");
        assertThat(run.getStatus()).isEqualTo(QUEUED);
        assertThat(run.getResumeCount()).isEqualTo(1);
        assertThat(run.getFinishedAt()).isNull();
    }
}
