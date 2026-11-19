package com.nexusai.citation;

import java.util.UUID;

/**
 * A retrieved passage offered to the model as evidence, numbered as the model sees it
 * ({@code [number]}). Only these can ever be cited.
 */
public record CitableSource(int number, UUID documentId, UUID chunkId, String documentTitle,
                            Integer pageNumber, int chunkIndex, int charStart, int charEnd,
                            String content, double score) {
}
