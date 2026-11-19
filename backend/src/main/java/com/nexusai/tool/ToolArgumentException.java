package com.nexusai.tool;

/** Arguments were well-formed but invalid in context (e.g. an unknown passage handle). */
public class ToolArgumentException extends RuntimeException {

    public ToolArgumentException(String message) {
        super(message);
    }
}
