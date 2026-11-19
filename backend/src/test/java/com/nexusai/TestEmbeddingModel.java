package com.nexusai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.web.client.ResourceAccessException;

/**
 * Test-only stand-in for the Ollama provider (the architecture tests services against
 * stubbed provider adapters). Deterministic bag-of-words hashing into 768 dimensions, so
 * texts sharing words are close in cosine space. It can simulate Ollama being down, failing
 * exactly like the real client (connection refused). Never used outside tests; the real
 * model is exercised by {@code OllamaSemanticSearchIT} and the manual smoke test.
 */
public class TestEmbeddingModel implements EmbeddingModel {

    public static final int DIMENSIONS = 768;

    private final AtomicBoolean available = new AtomicBoolean(true);
    private final AtomicInteger calls = new AtomicInteger();
    private volatile int dimensions = DIMENSIONS;

    public void setAvailable(boolean value) {
        available.set(value);
    }

    /** Simulates a misconfigured model (wrong vector size). */
    public void setDimensions(int value) {
        dimensions = value;
    }

    public int calls() {
        return calls.get();
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        calls.incrementAndGet();
        if (!available.get()) {
            throw new ResourceAccessException("I/O error on POST request for \"http://localhost:11434/api/embed\": Connection refused");
        }
        List<Embedding> results = new ArrayList<>();
        List<String> inputs = request.getInstructions();
        for (int i = 0; i < inputs.size(); i++) {
            results.add(new Embedding(vectorize(inputs.get(i), dimensions), i));
        }
        return new EmbeddingResponse(results);
    }

    @Override
    public float[] embed(Document document) {
        return call(new EmbeddingRequest(List.of(document.getText()), null)).getResult().getOutput();
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    static float[] vectorize(String text, int dimensions) {
        float[] vector = new float[dimensions];
        for (String token : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (token.length() < 3 || token.startsWith("search_") || token.equals("search")
                    || token.equals("document") || token.equals("query")) {
                continue; // ignore stop-length words and the task prefixes
            }
            int hash = token.hashCode();
            vector[Math.floorMod(hash, dimensions)] += (hash & 1) == 0 ? 1f : -1f;
        }
        double norm = 0;
        for (float v : vector) norm += v * v;
        if (norm == 0) {
            vector[0] = 1f; // keep the vector valid for cosine distance
            return vector;
        }
        float scale = (float) (1 / Math.sqrt(norm));
        for (int i = 0; i < dimensions; i++) vector[i] *= scale;
        return vector;
    }
}
