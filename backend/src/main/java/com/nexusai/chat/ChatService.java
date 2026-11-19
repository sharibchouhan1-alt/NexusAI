package com.nexusai.chat;

import com.nexusai.chat.ChatDtos.ConversationDetail;
import com.nexusai.chat.ChatDtos.ConversationSummary;
import com.nexusai.chat.ChatDtos.MessageView;
import com.nexusai.chat.ChatDtos.TurnResponse;
import com.nexusai.chat.ChatMessage.Role;
import com.nexusai.citation.CitationService;
import com.nexusai.citation.CitationView;
import com.nexusai.platform.web.ConflictException;
import com.nexusai.platform.web.PageResponse;
import com.nexusai.platform.web.ResourceNotFoundException;
import com.nexusai.rag.RagAnswer;
import com.nexusai.rag.RagService;
import com.nexusai.workspace.WorkspaceService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Published interface of the chat module: workspace-scoped conversations and their turns.
 *
 * <p>A turn is persisted in short transactions around the (slow) RAG call: (1) lock the
 * conversation, store the question and a PENDING answer; (2) run RAG with no transaction
 * open; (3) store the outcome and its verified citations. One answer per conversation is in
 * flight at a time; a server stop leaves a PENDING answer that startup marks FAILED.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    static final String INTERRUPTED = "Answer generation was interrupted by a server restart. Retry to generate it.";
    private static final int TITLE_LENGTH = 80;

    private final ConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final WorkspaceService workspaces;
    private final RagService rag;
    private final CitationService citations;
    private final TransactionTemplate transactions;

    ChatService(ConversationRepository conversations, ChatMessageRepository messages, WorkspaceService workspaces,
                RagService rag, CitationService citations, TransactionTemplate transactions) {
        this.conversations = conversations;
        this.messages = messages;
        this.workspaces = workspaces;
        this.rag = rag;
        this.citations = citations;
        this.transactions = transactions;
    }

    // --- conversations ---

    @Transactional
    public ConversationSummary create(UUID workspaceId, String title) {
        workspaces.requireExists(workspaceId);
        String effective = title == null || title.isBlank() ? Conversation.DEFAULT_TITLE : title.strip();
        return summary(conversations.saveAndFlush(new Conversation(workspaceId, effective)));
    }

    @Transactional(readOnly = true)
    public PageResponse<ConversationSummary> list(UUID workspaceId, int page, int size) {
        workspaces.requireExists(workspaceId);
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.asc("id")));
        return PageResponse.of(conversations.findByWorkspaceId(workspaceId, pageable), this::summary);
    }

    @Transactional(readOnly = true)
    public ConversationDetail get(UUID workspaceId, UUID conversationId) {
        Conversation c = find(workspaceId, conversationId);
        List<ChatMessage> turns = messages.findByConversationIdAndWorkspaceIdOrderBySeq(c.getId(), workspaceId);
        Map<UUID, List<CitationView>> evidence = citations.forMessages(workspaceId,
                turns.stream().map(ChatMessage::getId).toList());
        return new ConversationDetail(c.getId(), c.getWorkspaceId(), c.getTitle(), c.getCreatedAt(), c.getUpdatedAt(),
                turns.stream().map(m -> view(m, evidence.getOrDefault(m.getId(), List.of()))).toList());
    }

    @Transactional
    public ConversationSummary rename(UUID workspaceId, UUID conversationId, String title) {
        Conversation c = find(workspaceId, conversationId);
        c.rename(title.strip());
        return summary(conversations.saveAndFlush(c));
    }

    /** Deletes the conversation, its messages and their citations (database cascade). */
    @Transactional
    public void delete(UUID workspaceId, UUID conversationId) {
        conversations.delete(find(workspaceId, conversationId));
    }

    // --- turns ---

    private record PendingTurn(UUID questionId, UUID answerId, String question, List<String> earlierQuestions) {}

    /** Asks a question; returns once the answer is complete, abstained or failed (never throws for those). */
    public TurnResponse ask(UUID workspaceId, UUID conversationId, String content) {
        PendingTurn turn = transactions.execute(status -> {
            Conversation c = lock(workspaceId, conversationId);
            List<ChatMessage> history = messages.findByConversationIdAndWorkspaceIdOrderBySeq(c.getId(), workspaceId);
            requireNoAnswerInFlight(history);
            int seq = messages.maxSeq(c.getId()) + 1;
            String question = content.strip();
            ChatMessage q = messages.saveAndFlush(ChatMessage.question(workspaceId, c.getId(), seq, question));
            ChatMessage a = messages.saveAndFlush(ChatMessage.pendingAnswer(workspaceId, c.getId(), seq + 1));
            if (c.hasDefaultTitle()) {
                c.rename(titleFrom(question));
            } else {
                c.touch();
            }
            conversations.saveAndFlush(c);
            return new PendingTurn(q.getId(), a.getId(), question, questionsIn(history));
        });
        return finish(workspaceId, conversationId, turn);
    }

    /** Regenerates a FAILED answer that is the latest message of its conversation. */
    public TurnResponse retry(UUID workspaceId, UUID conversationId, UUID answerId) {
        PendingTurn turn = transactions.execute(status -> {
            Conversation c = lock(workspaceId, conversationId);
            List<ChatMessage> history = messages.findByConversationIdAndWorkspaceIdOrderBySeq(c.getId(), workspaceId);
            ChatMessage answer = history.stream().filter(m -> m.getId().equals(answerId)).findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Message", answerId));
            if (answer.getRole() != Role.ASSISTANT || answer.getStatus() != MessageStatus.FAILED) {
                throw new ConflictException("Only a failed answer can be retried");
            }
            if (history.getLast() != answer || history.size() < 2) {
                throw new ConflictException("Only the latest answer of a conversation can be retried");
            }
            ChatMessage question = history.get(history.size() - 2);
            answer.restartGeneration();
            messages.saveAndFlush(answer);
            c.touch();
            conversations.saveAndFlush(c);
            return new PendingTurn(question.getId(), answer.getId(), question.getContent(),
                    questionsIn(history.subList(0, history.size() - 2)));
        });
        return finish(workspaceId, conversationId, turn);
    }

    private TurnResponse finish(UUID workspaceId, UUID conversationId, PendingTurn turn) {
        RagAnswer result;
        try {
            result = rag.answer(workspaceId, turn.question(), turn.earlierQuestions());
        } catch (RuntimeException e) {
            log.error("Unexpected failure while answering in conversation {}", conversationId, e);
            result = new RagAnswer(RagAnswer.Outcome.FAILED, null, List.of(),
                    "An unexpected error occurred while answering. Retry the question.", null, 0, 0);
        }
        RagAnswer outcome = result;
        return transactions.execute(status -> {
            ChatMessage answer = messages.findByIdAndWorkspaceId(turn.answerId(), workspaceId)
                    // The conversation was deleted while the model was working.
                    .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));
            MessageStatus next = switch (outcome.outcome()) {
                case ANSWERED -> MessageStatus.COMPLETE;
                case ABSTAINED -> MessageStatus.ABSTAINED;
                case FAILED -> MessageStatus.FAILED;
            };
            answer.complete(next, outcome.text(), outcome.detail(), outcome.model(), outcome.retrievedCount(),
                    outcome.latencyMs());
            messages.saveAndFlush(answer);
            citations.replace(workspaceId, answer.getId(), outcome.citations());
            Map<UUID, List<CitationView>> evidence = citations.forMessages(workspaceId, List.of(answer.getId()));
            ChatMessage question = messages.findByIdAndWorkspaceId(turn.questionId(), workspaceId).orElseThrow();
            return new TurnResponse(view(question, List.of()), view(answer, evidence.getOrDefault(answer.getId(), List.of())));
        });
    }

    /** Answers left PENDING when the server stopped can't complete; mark them retryable. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void failInterruptedAnswers() {
        List<ChatMessage> pending = messages.findByStatus(MessageStatus.PENDING);
        pending.forEach(m -> {
            m.complete(MessageStatus.FAILED, "", INTERRUPTED, m.getModel(), 0, 0);
            messages.saveAndFlush(m);
        });
        if (!pending.isEmpty()) {
            log.info("Marked {} interrupted answer(s) as failed", pending.size());
        }
    }

    // --- helpers ---

    private static void requireNoAnswerInFlight(List<ChatMessage> history) {
        if (history.stream().anyMatch(m -> m.getStatus() == MessageStatus.PENDING)) {
            throw new ConflictException("The previous question is still being answered");
        }
    }

    private static List<String> questionsIn(List<ChatMessage> history) {
        return history.stream().filter(m -> m.getRole() == Role.USER).map(ChatMessage::getContent).toList();
    }

    private static String titleFrom(String question) {
        String oneLine = question.replaceAll("\\s+", " ").strip();
        return oneLine.length() <= TITLE_LENGTH ? oneLine : oneLine.substring(0, TITLE_LENGTH - 1).strip() + "…";
    }

    private Conversation find(UUID workspaceId, UUID conversationId) {
        workspaces.requireExists(workspaceId);
        return conversations.findByIdAndWorkspaceId(conversationId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));
    }

    private Conversation lock(UUID workspaceId, UUID conversationId) {
        workspaces.requireExists(workspaceId);
        return conversations.lockByIdAndWorkspaceId(conversationId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));
    }

    private ConversationSummary summary(Conversation c) {
        return new ConversationSummary(c.getId(), c.getWorkspaceId(), c.getTitle(), messages.countByConversationId(c.getId()),
                c.getCreatedAt(), c.getUpdatedAt());
    }

    private static MessageView view(ChatMessage m, List<CitationView> evidence) {
        return new MessageView(m.getId(), m.getConversationId(), m.getRole().name(), m.getContent(), m.getStatus(),
                m.getStatusDetail(), m.getModel(), m.getRetrievedCount(), m.getLatencyMs(), m.getCreatedAt(), evidence);
    }
}
