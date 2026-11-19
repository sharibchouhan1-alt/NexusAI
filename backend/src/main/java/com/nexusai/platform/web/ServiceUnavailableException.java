package com.nexusai.platform.web;

import java.time.Duration;

/** A dependency the request needs is temporarily unavailable (HTTP 503, retryable). */
public class ServiceUnavailableException extends RuntimeException {

    private final Duration retryAfter;

    public ServiceUnavailableException(String message, Duration retryAfter, Throwable cause) {
        super(message, cause);
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
