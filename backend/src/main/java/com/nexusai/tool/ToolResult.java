package com.nexusai.tool;

import java.util.List;

/**
 * What a successful tool call produced: a one-line summary for the audit trail and the
 * passages (with their handles) the agent observes next.
 */
public record ToolResult(String summary, List<HandledPassage> passages) {

    public record HandledPassage(String handle, Passage passage) {}

    public ToolResult {
        passages = passages == null ? List.of() : List.copyOf(passages);
    }
}
