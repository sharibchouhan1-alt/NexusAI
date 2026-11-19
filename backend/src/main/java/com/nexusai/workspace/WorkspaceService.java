package com.nexusai.workspace;

import com.nexusai.platform.web.ConflictException;
import com.nexusai.platform.web.PageResponse;
import com.nexusai.platform.web.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Published interface of the workspace module. */
@Service
public class WorkspaceService {

    private final WorkspaceRepository repository;

    WorkspaceService(WorkspaceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public WorkspaceResponse create(WorkspaceRequest request) {
        Workspace workspace = new Workspace(request.name().strip(), normalize(request.description()));
        return WorkspaceResponse.from(repository.saveAndFlush(workspace));
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkspaceResponse> list(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt", "id"));
        return PageResponse.of(repository.findAll(pageable), WorkspaceResponse::from);
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse get(UUID id) {
        return WorkspaceResponse.from(find(id));
    }

    @Transactional
    public WorkspaceResponse update(UUID id, WorkspaceRequest request) {
        Workspace workspace = find(id);
        workspace.rename(request.name().strip(), normalize(request.description()));
        return WorkspaceResponse.from(repository.saveAndFlush(workspace));
    }

    /** Deletes an empty workspace. A workspace that still holds documents is rejected. */
    @Transactional
    public void delete(UUID id) {
        repository.delete(find(id));
        try {
            repository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Workspace " + id + " is not empty; delete its documents first");
        }
    }

    /**
     * The check every workspace-scoped operation performs before touching knowledge.
     * Throws {@link ResourceNotFoundException} if the workspace does not exist.
     */
    @Transactional(readOnly = true)
    public void requireExists(UUID id) {
        if (!repository.existsById(id)) {
            throw notFound(id);
        }
    }

    private Workspace find(UUID id) {
        return repository.findById(id).orElseThrow(() -> notFound(id));
    }

    private static ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("Workspace", id);
    }

    private static String normalize(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
