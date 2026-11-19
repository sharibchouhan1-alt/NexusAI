package com.nexusai.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Editable metadata. Source type and origin identify the source and are immutable. */
public record UpdateDocumentRequest(@NotBlank @Size(max = 500) String title) {
}
