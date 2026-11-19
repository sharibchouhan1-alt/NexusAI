package com.nexusai.citation;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Internal to the citation module; always scoped by workspace. */
interface CitationRepository extends Repository<Citation, UUID> {

    Citation save(Citation citation);

    List<Citation> findByWorkspaceIdAndMessageIdInOrderByOrdinal(UUID workspaceId, Collection<UUID> messageIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Citation c where c.workspaceId = :workspaceId and c.messageId = :messageId")
    void deleteForMessage(@Param("workspaceId") UUID workspaceId, @Param("messageId") UUID messageId);

    List<Citation> findByWorkspaceIdAndResearchRunIdOrderByOrdinal(UUID workspaceId, UUID researchRunId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Citation c where c.workspaceId = :workspaceId and c.researchRunId = :runId")
    void deleteForResearchRun(@Param("workspaceId") UUID workspaceId, @Param("runId") UUID runId);
}
