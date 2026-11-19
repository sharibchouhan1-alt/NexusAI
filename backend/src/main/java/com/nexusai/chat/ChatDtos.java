package com.nexusai.chat;

import com.nexusai.citation.CitationView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request and response bodies of the chat API. */
public final class ChatDtos {

    private ChatDtos() {
    }

    public record CreateConversationRequest(@Size(max = 200) String title) {}

    public record RenameConversationRequest(@NotBlank @Size(max = 200) String title) {}

    public record AskRequest(@NotBlank @Size(max = 4000) String content) {}

    public record ConversationSummary(UUID id, UUID workspaceId, String title, long messageCount,
                                      Instant createdAt, Instant updatedAt) {}

    public record ConversationDetail(UUID id, UUID workspaceId, String title, Instant createdAt, Instant updatedAt,
                                     List<MessageView> messages) {}

    /**
     * @param content      question, verified answer (with [n] markers), or the abstention text
     * @param statusDetail why an answer abstained or failed
     * @param citations    verified evidence for an answer, ordered by marker number
     */
    public record MessageView(UUID id, UUID conversationId, String role, String content, MessageStatus status,
                              String statusDetail, String model, Integer retrievedCount, Integer latencyMs,
                              Instant createdAt, List<CitationView> citations) {}

    /** The question as stored and the answer outcome (COMPLETE, ABSTAINED or FAILED). */
    public record TurnResponse(MessageView question, MessageView answer) {}
}
