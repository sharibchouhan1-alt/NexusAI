package com.nexusai.document;

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
@RequestMapping("/api/workspaces/{workspaceId}/documents")
class DocumentController {

    private final DocumentService service;

    DocumentController(DocumentService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<DocumentResponse> register(@PathVariable UUID workspaceId,
                                              @Valid @RequestBody CreateDocumentRequest request) {
        DocumentResponse created = service.register(workspaceId, request);
        URI location = URI.create("/api/workspaces/" + workspaceId + "/documents/" + created.id());
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    PageResponse<DocumentResponse> list(
            @PathVariable UUID workspaceId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(workspaceId, page, size);
    }

    @GetMapping("/{documentId}")
    DocumentResponse get(@PathVariable UUID workspaceId, @PathVariable UUID documentId) {
        return service.get(workspaceId, documentId);
    }

    @PutMapping("/{documentId}")
    DocumentResponse update(@PathVariable UUID workspaceId, @PathVariable UUID documentId,
                            @Valid @RequestBody UpdateDocumentRequest request) {
        return service.update(workspaceId, documentId, request);
    }

    @DeleteMapping("/{documentId}")
    ResponseEntity<Void> delete(@PathVariable UUID workspaceId, @PathVariable UUID documentId) {
        service.delete(workspaceId, documentId);
        return ResponseEntity.noContent().build();
    }
}
