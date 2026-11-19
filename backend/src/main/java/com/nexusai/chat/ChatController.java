package com.nexusai.chat;

import com.nexusai.chat.ChatDtos.AskRequest;
import com.nexusai.chat.ChatDtos.ConversationDetail;
import com.nexusai.chat.ChatDtos.ConversationSummary;
import com.nexusai.chat.ChatDtos.CreateConversationRequest;
import com.nexusai.chat.ChatDtos.RenameConversationRequest;
import com.nexusai.chat.ChatDtos.TurnResponse;
import com.nexusai.platform.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/conversations")
class ChatController {

    private final ChatService service;

    ChatController(ChatService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<ConversationSummary> create(@PathVariable UUID workspaceId,
                                               @Valid @RequestBody(required = false) CreateConversationRequest request) {
        ConversationSummary created = service.create(workspaceId, request == null ? null : request.title());
        return ResponseEntity.created(URI.create("/api/workspaces/" + workspaceId + "/conversations/" + created.id()))
                .body(created);
    }

    @GetMapping
    PageResponse<ConversationSummary> list(@PathVariable UUID workspaceId,
                                           @RequestParam(defaultValue = "0") @Min(0) int page,
                                           @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(workspaceId, page, size);
    }

    @GetMapping("/{conversationId}")
    ConversationDetail get(@PathVariable UUID workspaceId, @PathVariable UUID conversationId) {
        return service.get(workspaceId, conversationId);
    }

    @PutMapping("/{conversationId}")
    ConversationSummary rename(@PathVariable UUID workspaceId, @PathVariable UUID conversationId,
                               @Valid @RequestBody RenameConversationRequest request) {
        return service.rename(workspaceId, conversationId, request.title());
    }

    @DeleteMapping("/{conversationId}")
    ResponseEntity<Void> delete(@PathVariable UUID workspaceId, @PathVariable UUID conversationId) {
        service.delete(workspaceId, conversationId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Asks a question. Blocks until the local model has answered (or abstained/failed) and
     * returns both persisted messages; 201 even when the answer FAILED (it is retryable).
     * 409 while a previous question in the conversation is still being answered.
     */
    @PostMapping("/{conversationId}/messages")
    ResponseEntity<TurnResponse> ask(@PathVariable UUID workspaceId, @PathVariable UUID conversationId,
                                     @Valid @RequestBody AskRequest request) {
        return ResponseEntity.status(201).body(service.ask(workspaceId, conversationId, request.content()));
    }

    /** Regenerates a FAILED answer (the latest message). */
    @PostMapping("/{conversationId}/messages/{messageId}/retry")
    TurnResponse retry(@PathVariable UUID workspaceId, @PathVariable UUID conversationId,
                       @PathVariable UUID messageId) {
        return service.retry(workspaceId, conversationId, messageId);
    }
}
