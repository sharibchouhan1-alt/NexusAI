package com.nexusai.tool;

/** The tool's capability failed. The message is user-safe. */
public class ToolExecutionException extends RuntimeException {

    public ToolExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
