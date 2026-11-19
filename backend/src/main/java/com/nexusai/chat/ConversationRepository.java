package com.nexusai.chat;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Internal to the chat module; every lookup is workspace-scoped. */
interface ConversationRepository extends Repository<Conversation, UUID> {

    Conversation saveAndFlush(Conversation conversation);

    Optional<Conversation> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    /** Serializes turns within one conversation (sequence numbers, one answer at a time). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.id = :id and c.workspaceId = :workspaceId")
    Optional<Conversation> lockByIdAndWorkspaceId(@Param("id") UUID id, @Param("workspaceId") UUID workspaceId);

    Page<Conversation> findByWorkspaceId(UUID workspaceId, Pageable pageable);

    void delete(Conversation conversation);
}
