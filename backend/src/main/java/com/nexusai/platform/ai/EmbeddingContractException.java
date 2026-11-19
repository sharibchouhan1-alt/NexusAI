package com.nexusai.platform.ai;

/**
 * The provider answered, but not with what the stored index requires (wrong dimension or
 * count). Not retryable without a configuration change.
 */
public class EmbeddingContractException extends RuntimeException {

    public EmbeddingContractException(String message) {
        super(message);
    }
}
