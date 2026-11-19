package com.nexusai.document;

/**
 * How much of a workspace is searchable right now.
 *
 * @param indexed   documents whose every chunk has an embedding
 * @param pending   documents waiting for or being given embeddings (includes ones still being ingested)
 * @param failed    documents whose indexing failed and needs attention
 */
public record SearchCoverage(long indexed, long pending, long failed) {
}
