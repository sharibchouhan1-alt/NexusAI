package com.nexusai.citation;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Published interface of the citation module: the single owner of evidence linkage.
 * Verifies what a model claims against what it was given, and persists verified
 * citations for the messages that carry them.
 */
@Service
public class CitationService {

    private final CitationRepository repository;
    private final CitationValidator validator;

    CitationService(CitationRepository repository, @Value("${nexusai.rag.min-quote-chars}") int minQuoteChars) {
        this.repository = repository;
        this.validator = new CitationValidator(minQuoteChars);
    }

    /** Checks the model's claimed citations against the passages it was actually given. */
    public ValidatedAnswer validate(ModelAnswer answer, List<CitableSource> sources) {
        return validator.validate(answer, sources);
    }

    /** Replaces the citations of a message. Joins the caller's transaction (the message write). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void replace(UUID workspaceId, UUID messageId, List<CitationDraft> citations) {
        repository.deleteForMessage(workspaceId, messageId);
        citations.forEach(c -> repository.save(Citation.forMessage(workspaceId, messageId, c)));
    }

    /** Replaces the citations of a research report. Joins the caller's transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void replaceForResearchRun(UUID workspaceId, UUID runId, List<CitationDraft> citations) {
        repository.deleteForResearchRun(workspaceId, runId);
        citations.forEach(c -> repository.save(Citation.forResearchRun(workspaceId, runId, c)));
    }

    @Transactional(readOnly = true)
    public List<CitationView> forResearchRun(UUID workspaceId, UUID runId) {
        return repository.findByWorkspaceIdAndResearchRunIdOrderByOrdinal(workspaceId, runId).stream()
                .map(Citation::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<CitationView>> forMessages(UUID workspaceId, Collection<UUID> messageIds) {
        if (messageIds.isEmpty()) {
            return Map.of();
        }
        return repository.findByWorkspaceIdAndMessageIdInOrderByOrdinal(workspaceId, messageIds).stream()
                .collect(Collectors.groupingBy(Citation::getMessageId,
                        Collectors.mapping(Citation::toView, Collectors.toList())));
    }
}
