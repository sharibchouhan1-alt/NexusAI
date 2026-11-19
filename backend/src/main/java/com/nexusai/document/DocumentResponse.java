package com.nexusai.document;

import java.time.Instant;
import java.util.UUID;

/**
 * Document metadata. The file fields and ingestion results are null for metadata-only
 * documents ({@code status = REGISTERED}). The storage location is never exposed.
 *
 * @param statusDetail user-facing reason when {@code status = FAILED}
 * @param indexStatus  search readiness of a READY document; null before chunks exist
 * @param indexDetail  user-facing note while waiting or after an indexing failure
 */
public record DocumentResponse(UUID id, UUID workspaceId, String title,
                               DocumentSourceType sourceType, String mediaType, String origin,
                               DocumentStatus status, String statusDetail,
                               Long fileSizeBytes, Integer pageCount, Integer chunkCount,
                               SearchIndexStatus indexStatus, String indexDetail,
                               Instant createdAt, Instant updatedAt) {

    static DocumentResponse from(Document d) {
        return new DocumentResponse(d.getId(), d.getWorkspaceId(), d.getTitle(),
                d.getSourceType(), d.getMediaType(), d.getOrigin(), d.getStatus(),
                d.getStatusDetail(), d.getFileSizeBytes(), d.getPageCount(), d.getChunkCount(),
                d.getIndexStatus(), d.getIndexDetail(), d.getCreatedAt(), d.getUpdatedAt());
    }
}
