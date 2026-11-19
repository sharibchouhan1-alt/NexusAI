package com.nexusai.platform.ai;

/**
 * The embedding provider could not produce embeddings right now (unreachable, timed out,
 * model missing, provider error). Retryable. The message is user-facing and contains no
 * provider internals; the cause carries the details for logs.
 */
public class EmbeddingUnavailableException extends RuntimeException {

    public EmbeddingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
