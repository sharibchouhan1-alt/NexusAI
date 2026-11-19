package com.nexusai.ingestion;

import com.nexusai.document.DocumentResponse;
import com.nexusai.document.DocumentService;
import com.nexusai.platform.web.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Ingestion results and controls for one document, always addressed through its workspace. */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/documents/{documentId}")
class IngestionController {

    private final DocumentService documents;
    private final ChunkRepository chunks;

    IngestionController(DocumentService documents, ChunkRepository chunks) {
        this.documents = documents;
        this.chunks = chunks;
    }

    /** Chunks in order, with page and offset provenance. 404 outside the document's workspace. */
    @GetMapping("/chunks")
    PageResponse<ChunkResponse> chunks(
            @PathVariable UUID workspaceId, @PathVariable UUID documentId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        documents.get(workspaceId, documentId); // workspace + existence check
        long total = chunks.count(workspaceId, documentId);
        return new PageResponse<>(chunks.page(workspaceId, documentId, page, size), page, size, total,
                (int) ((total + size - 1) / size));
    }

    /** Re-runs ingestion of the stored original (e.g. after a failure). 409 if in flight. */
    @PostMapping("/ingestion")
    ResponseEntity<DocumentResponse> reingest(@PathVariable UUID workspaceId, @PathVariable UUID documentId) {
        return ResponseEntity.accepted().body(documents.requestIngestion(workspaceId, documentId));
    }
}
