package com.nexusai.document;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(UUID id, UUID workspaceId, String title,
                               DocumentSourceType sourceType, String mediaType, String origin,
                               DocumentStatus status, Instant createdAt, Instant updatedAt) {

    static DocumentResponse from(Document d) {
        return new DocumentResponse(d.getId(), d.getWorkspaceId(), d.getTitle(),
                d.getSourceType(), d.getMediaType(), d.getOrigin(), d.getStatus(),
                d.getCreatedAt(), d.getUpdatedAt());
    }
}
