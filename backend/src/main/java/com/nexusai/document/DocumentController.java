package com.nexusai.document;

import com.nexusai.platform.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
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

    /**
     * Uploads a PDF or TXT original (multipart field {@code file}, optional {@code title}).
     * Returns 202: the document is stored and PENDING; ingestion continues in the background.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<DocumentResponse> upload(@PathVariable UUID workspaceId,
                                            @RequestPart("file") MultipartFile file,
                                            @RequestParam(value = "title", required = false)
                                            @Size(max = 500) String title) {
        DocumentResponse created = service.upload(workspaceId, file, title);
        URI location = URI.create("/api/workspaces/" + workspaceId + "/documents/" + created.id());
        return ResponseEntity.accepted().location(location).body(created);
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
