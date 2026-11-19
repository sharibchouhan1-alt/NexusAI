package com.nexusai.tool;

import java.util.Objects;
import java.util.UUID;

/**
 * Everything a tool may act on: the workspace of the run (the only scope tools ever use)
 * and the ledger in which retrieved evidence is recorded with provenance.
 */
public record ToolContext(UUID workspaceId, EvidenceLedger ledger) {

    public ToolContext {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(ledger, "ledger");
    }
}
