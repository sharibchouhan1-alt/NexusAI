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

/** Internal to the research module; user-facing lookups are workspace-scoped. */
interface ResearchRunRepository extends Repository<ResearchRun, UUID> {

    ResearchRun saveAndFlush(ResearchRun run);

    Optional<ResearchRun> findById(UUID id);

    Optional<ResearchRun> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Page<ResearchRun> findByWorkspaceId(UUID workspaceId, Pageable pageable);

    List<ResearchRun> findByStatusIn(Collection<ResearchStatus> statuses);

    void delete(ResearchRun run);

    /** QUEUED → PLANNING as one conditional update: exactly one worker claims a run. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ResearchRun r set r.status = com.nexusai.research.ResearchStatus.PLANNING,
                   r.statusDetail = null, r.updatedAt = :now,
                   r.startedAt = coalesce(r.startedAt, :now)
             where r.id = :id and r.status = com.nexusai.research.ResearchStatus.QUEUED
               and r.cancelRequested = false
            """)
    int claim(@Param("id") UUID id, @Param("now") Instant now);

    @Query("select r.cancelRequested from ResearchRun r where r.id = :id")
    Optional<Boolean> cancelRequested(@Param("id") UUID id);
}
