package com.nexusai.workspace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for creating or replacing a workspace. */
public record WorkspaceRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description) {
}
