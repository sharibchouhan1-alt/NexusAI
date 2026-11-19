package com.nexusai.platform.ai;

/**
 * The generation model could not produce an answer (unreachable, model missing, provider
 * error, or timed out). Retryable. The message is user-facing; the cause has the details.
 */
public class GenerationUnavailableException extends RuntimeException {

    private final boolean timeout;

    public GenerationUnavailableException(String message, boolean timeout, Throwable cause) {
        super(message, cause);
        this.timeout = timeout;
    }

    public boolean isTimeout() {
        return timeout;
    }
}
