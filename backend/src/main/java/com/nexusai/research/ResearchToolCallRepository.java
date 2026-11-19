package com.nexusai.research;

import com.nexusai.tool.ToolInvocation;
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
interface ResearchToolCallRepository extends Repository<ResearchToolCall, UUID> {

    ResearchToolCall saveAndFlush(ResearchToolCall call);

    List<ResearchToolCall> findByRunIdOrderBySeq(UUID runId);

    List<ResearchToolCall> findByRunIdAndStepIdAndStatusOrderBySeq(UUID runId, UUID stepId, ToolInvocation.Status status);

    @Query("select coalesce(max(c.seq), 0) from ResearchToolCall c where c.runId = :runId")
    int maxSeq(@Param("runId") UUID runId);

    @Query("select count(c) from ResearchToolCall c where c.runId = :runId and c.status = com.nexusai.tool.ToolInvocation.Status.SUCCEEDED")
    long countSucceeded(@Param("runId") UUID runId);

    Optional<ResearchToolCall> findFirstByRunIdAndErrorIsNotNullOrderBySeqDesc(UUID runId);
}
