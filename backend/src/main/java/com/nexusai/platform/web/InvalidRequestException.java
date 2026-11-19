package com.nexusai.platform.web;

/** A request that passed syntactic validation but is semantically invalid (HTTP 400). */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
