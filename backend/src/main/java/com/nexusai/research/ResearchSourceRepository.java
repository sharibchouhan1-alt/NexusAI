package com.nexusai.research;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Internal to the research module. */
interface ResearchSourceRepository extends Repository<ResearchSource, UUID> {

    ResearchSource saveAndFlush(ResearchSource source);

    List<ResearchSource> findByRunId(UUID runId);

    Optional<ResearchSource> findByRunIdAndChunkId(UUID runId, UUID chunkId);

    Optional<ResearchSource> findByRunIdAndHandle(UUID runId, String handle);

    long countByRunId(UUID runId);
}
