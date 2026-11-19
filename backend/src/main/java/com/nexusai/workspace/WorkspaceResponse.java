package com.nexusai.workspace;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceResponse(UUID id, String name, String description,
                                Instant createdAt, Instant updatedAt) {

    static WorkspaceResponse from(Workspace w) {
        return new WorkspaceResponse(w.getId(), w.getName(), w.getDescription(),
                w.getCreatedAt(), w.getUpdatedAt());
    }
}
