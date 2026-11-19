package com.nexusai.chat;

/** Lifecycle of a chat message. User messages are always COMPLETE. */
public enum MessageStatus {
    /** A user question, or an answer grounded in verified citations. */
    COMPLETE,
    /** An answer is being generated. */
    PENDING,
    /** No answer was given because the evidence didn't support one (see statusDetail). */
    ABSTAINED,
    /** Generation failed (model or search unavailable, timeout, bad output); retryable. */
    FAILED
}
