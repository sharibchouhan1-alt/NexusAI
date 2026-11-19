package com.nexusai.chat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Internal to the chat module; every lookup is workspace-scoped. */
interface ChatMessageRepository extends Repository<ChatMessage, UUID> {

    ChatMessage saveAndFlush(ChatMessage message);

    Optional<ChatMessage> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<ChatMessage> findByConversationIdAndWorkspaceIdOrderBySeq(UUID conversationId, UUID workspaceId);

    Optional<ChatMessage> findFirstByConversationIdAndWorkspaceIdOrderBySeqDesc(UUID conversationId, UUID workspaceId);

    @Query("select coalesce(max(m.seq), -1) from ChatMessage m where m.conversationId = :conversationId")
    int maxSeq(@Param("conversationId") UUID conversationId);

    /** System-level: answers left PENDING by a stopped server. */
    List<ChatMessage> findByStatus(MessageStatus status);

    long countByConversationId(UUID conversationId);
}
