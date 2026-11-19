package com.nexusai.document;

import com.nexusai.platform.web.PageResponse;
import com.nexusai.platform.web.ResourceNotFoundException;
import com.nexusai.workspace.WorkspaceService;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Published interface of the document module. Every operation takes the workspace id
 * explicitly; a document in another workspace is indistinguishable from a missing one.
 */
@Service
public class DocumentService {

    private final DocumentRepository repository;
    private final WorkspaceService workspaces;

    DocumentService(DocumentRepository repository, WorkspaceService workspaces) {
        this.repository = repository;
        this.workspaces = workspaces;
    }

    @Transactional
    public DocumentResponse register(UUID workspaceId, CreateDocumentRequest request) {
        workspaces.requireExists(workspaceId);
        Document document = new Document(workspaceId, request.title().strip(),
                request.sourceType(), normalize(request.mediaType()), normalize(request.origin()));
        try {
            return DocumentResponse.from(repository.saveAndFlush(document));
        } catch (DataIntegrityViolationException e) {
            // Workspace deleted concurrently between the check and the insert.
            throw new ResourceNotFoundException("Workspace", workspaceId);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentResponse> list(UUID workspaceId, int page, int size) {
        workspaces.requireExists(workspaceId);
        var pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        return PageResponse.of(repository.findByWorkspaceId(workspaceId, pageable),
                DocumentResponse::from);
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(UUID workspaceId, UUID documentId) {
        return DocumentResponse.from(find(workspaceId, documentId));
    }

    @Transactional
    public DocumentResponse update(UUID workspaceId, UUID documentId, UpdateDocumentRequest request) {
        Document document = find(workspaceId, documentId);
        document.retitle(request.title().strip());
        return DocumentResponse.from(repository.saveAndFlush(document));
    }

    @Transactional
    public void delete(UUID workspaceId, UUID documentId) {
        repository.delete(find(workspaceId, documentId));
    }

    private Document find(UUID workspaceId, UUID documentId) {
        workspaces.requireExists(workspaceId);
        return repository.findByIdAndWorkspaceId(documentId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
    }

    private static String normalize(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
