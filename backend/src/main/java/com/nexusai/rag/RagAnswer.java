package com.nexusai.rag;

import com.nexusai.citation.CitationDraft;
import java.util.List;

/**
 * Outcome of one grounded-answer attempt. Only ANSWERED carries text meant to be shown as an
 * answer; ABSTAINED and FAILED carry a user-facing {@code detail}.
 *
 * @param retrievedCount passages given to the model as evidence
 */
public record RagAnswer(Outcome outcome, String text, List<CitationDraft> citations, String detail,
                        String model, int retrievedCount, long latencyMs) {

    public enum Outcome { ANSWERED, ABSTAINED, FAILED }

    /** Shown in place of an answer whenever the evidence does not support one. */
    public static final String ABSTENTION_TEXT =
            "I couldn't find enough evidence in this workspace's documents to answer that.";
}
