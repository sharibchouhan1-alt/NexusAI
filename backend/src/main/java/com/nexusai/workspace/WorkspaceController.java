package com.nexusai.workspace;

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
@RequestMapping("/api/workspaces")
class WorkspaceController {

    private final WorkspaceService service;

    WorkspaceController(WorkspaceService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<WorkspaceResponse> create(@Valid @RequestBody WorkspaceRequest request) {
        WorkspaceResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/workspaces/" + created.id())).body(created);
    }

    @GetMapping
    PageResponse<WorkspaceResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(page, size);
    }

    @GetMapping("/{workspaceId}")
    WorkspaceResponse get(@PathVariable UUID workspaceId) {
        return service.get(workspaceId);
    }

    @PutMapping("/{workspaceId}")
    WorkspaceResponse update(@PathVariable UUID workspaceId,
                             @Valid @RequestBody WorkspaceRequest request) {
        return service.update(workspaceId, request);
    }

    @DeleteMapping("/{workspaceId}")
    ResponseEntity<Void> delete(@PathVariable UUID workspaceId) {
        service.delete(workspaceId);
        return ResponseEntity.noContent().build();
    }
}
