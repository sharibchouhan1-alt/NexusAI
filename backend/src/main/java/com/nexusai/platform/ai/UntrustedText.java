package com.nexusai.platform.ai;

import java.util.regex.Pattern;

/**
 * Helpers for placing untrusted text (document passages, user input, tool output) inside
 * model prompts. Prompts delimit such text with tags; anything inside the text that looks
 * like one of those tags is neutralized so it can't close a block or fake a new one.
 */
public final class UntrustedText {

    private static final Pattern DELIMITER_LOOKALIKE = Pattern.compile(
            "<\\s*/?\\s*(sources?|question|history|system|instructions?|objective|step|observations?|passage|tool|tools)\\b[^>]*>",
            Pattern.CASE_INSENSITIVE);

    private UntrustedText() {
    }

    /** Replaces tag look-alikes that could break out of a prompt block. */
    public static String neutralize(String text) {
        return text == null ? "" : DELIMITER_LOOKALIKE.matcher(text).replaceAll("[removed tag]");
    }

    /** Neutralized, single-line, quote-safe value for use inside a tag attribute. */
    public static String attribute(String value) {
        return neutralize(value).replace("\"", "'").replace('\n', ' ').replace('\r', ' ');
    }

    /** Neutralized and truncated to {@code max} characters (with an ellipsis). */
    public static String excerpt(String text, int max) {
        String clean = neutralize(text);
        return clean.length() <= max ? clean : clean.substring(0, Math.max(0, max - 1)) + "…";
    }
}
