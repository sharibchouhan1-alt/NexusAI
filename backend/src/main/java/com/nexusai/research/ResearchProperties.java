package com.nexusai.research;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code nexusai.research.*}: hard bounds that guarantee every run terminates.
 *
 * @param maxSteps            steps a plan may contain
 * @param maxActionsPerStep   agent decisions per step
 * @param maxToolCalls        tool invocations per run (all steps, including resumptions)
 * @param maxSources          distinct passages a run may gather
 * @param maxDuration         wall-clock budget per execution attempt
 * @param synthesisMaxSources passages given to the model when writing the report
 * @param synthesisMaxChars   character budget for those passages
 * @param maxResumes          automatic + manual resumptions before a run is abandoned
 */
@Validated
@ConfigurationProperties("nexusai.research")
record ResearchProperties(
        @Min(1) @Max(8) int maxSteps,
        @Min(1) @Max(10) int maxActionsPerStep,
        @Min(1) @Max(100) int maxToolCalls,
        @Min(1) @Max(200) int maxSources,
        @NotNull Duration maxDuration,
        @Min(1) @Max(30) int synthesisMaxSources,
        @Min(1000) int synthesisMaxChars,
        @Min(0) @Max(10) int maxResumes,
        @Min(1) @Max(8) int workerThreads,
        @Min(1) int queueCapacity) {
}
