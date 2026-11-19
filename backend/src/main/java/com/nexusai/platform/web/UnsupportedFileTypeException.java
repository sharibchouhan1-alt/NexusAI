package com.nexusai.platform.web;

/** An uploaded file whose type is not accepted, or whose content contradicts its name (HTTP 415). */
public class UnsupportedFileTypeException extends RuntimeException {

    public UnsupportedFileTypeException(String message) {
        super(message);
    }
}
