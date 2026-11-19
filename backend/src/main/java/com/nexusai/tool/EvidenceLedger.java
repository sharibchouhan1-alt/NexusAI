package com.nexusai.tool;

import java.util.Optional;
import java.util.UUID;

/**
 * Where tools record the passages they retrieve. Each distinct chunk gets one stable handle
 * per run ({@code P1}, {@code P2}, ...) so the agent can refer to evidence without copying
 * identifiers, and provenance is stored as soon as evidence is seen.
 */
public interface EvidenceLedger {

    /**
     * Records the passage (once per chunk) and returns its handle, or empty if the run's
     * evidence limit has been reached.
     */
    Optional<String> record(Passage passage);

    /** The chunk behind a handle previously returned in this run. */
    Optional<UUID> resolve(String handle);
}
