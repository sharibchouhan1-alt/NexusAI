package com.nexusai.citation;

import java.util.List;

/**
 * What the model claims, before verification: untrusted. {@code citations} reference
 * source numbers and quotes that must be checked against the actual passages.
 */
public record ModelAnswer(boolean answerable, String answer, List<Claim> citations) {

    public record Claim(int source, String quote) {}

    public ModelAnswer {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
