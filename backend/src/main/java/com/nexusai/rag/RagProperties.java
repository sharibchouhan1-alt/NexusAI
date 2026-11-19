package com.nexusai.rag;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code nexusai.rag.*}
 *
 * @param topK             most passages offered to the model as evidence
 * @param minScore         cosine similarity below which a passage is not considered evidence
 * @param maxContextChars  budget for all passages in the prompt
 * @param minQuoteChars    shortest citation quote accepted as verification
 * @param historyQuestions earlier questions of the conversation given for disambiguation
 */
@Validated
@ConfigurationProperties("nexusai.rag")
record RagProperties(
        @Min(1) @Max(20) int topK,
        @DecimalMin("-1.0") @DecimalMax("1.0") double minScore,
        @Min(500) int maxContextChars,
        @Min(1) int minQuoteChars,
        @Min(0) @Max(10) int historyQuestions) {
}
