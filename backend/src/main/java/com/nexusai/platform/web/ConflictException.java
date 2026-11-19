package com.nexusai.platform.web;

/** The request is valid but conflicts with the current state of the resource. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
