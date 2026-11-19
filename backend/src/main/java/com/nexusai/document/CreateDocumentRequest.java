package com.nexusai.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registers a knowledge source's metadata. No file content is accepted.
 *
 * @param origin where the source came from: original file name or URL
 */
public record CreateDocumentRequest(
        @NotBlank @Size(max = 500) String title,
        @NotNull DocumentSourceType sourceType,
        @Size(max = 255)
        @Pattern(regexp = "^[\\w.+-]+/[\\w.+-]+$", message = "must be a media type such as application/pdf")
        String mediaType,
        @Size(max = 2048) String origin) {
}
