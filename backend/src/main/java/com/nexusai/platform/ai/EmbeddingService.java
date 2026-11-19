package com.nexusai.platform.ai;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * Provider adapter for the embedding capability (Spring AI → Ollama nomic-embed-text).
 * The only place Spring AI types appear. Applies the model's task prefixes, enforces the
 * vector contract, and turns every provider failure into {@link EmbeddingUnavailableException}.
 */
@Service
@EnableConfigurationProperties(EmbeddingProperties.class)
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final EmbeddingModel model;
    private final EmbeddingProperties properties;
    private final String modelId;

    public EmbeddingService(EmbeddingModel model, EmbeddingProperties properties,
                            @Value("${spring.ai.ollama.embedding.options.model}") String modelName) {
        this.model = model;
        this.properties = properties;
        this.modelId = "ollama:" + modelName;
    }

    /** Identifies the vector space; stored with every embedding. Vectors of different models never mix. */
    public String modelId() {
        return modelId;
    }

    /** Embeds chunk texts for storage, in order. */
    public List<float[]> embedDocuments(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        List<String> inputs = texts.stream().map(t -> properties.documentPrefix() + t).toList();
        List<float[]> vectors = call(inputs);
        if (vectors.size() != texts.size()) {
            throw new EmbeddingContractException("The embedding model returned " + vectors.size()
                    + " vectors for " + texts.size() + " inputs.");
        }
        vectors.forEach(this::checkDimensions);
        return vectors;
    }

    /** Embeds a search query. */
    public float[] embedQuery(String query) {
        float[] vector = call(List.of(properties.queryPrefix() + query)).getFirst();
        checkDimensions(vector);
        return vector;
    }

    /** Cheap liveness probe: true if the provider can embed right now. */
    public boolean isAvailable() {
        try {
            call(List.of("ping"));
            return true;
        } catch (EmbeddingUnavailableException e) {
            return false;
        }
    }

    private List<float[]> call(List<String> inputs) {
        try {
            return model.embed(inputs);
        } catch (RuntimeException e) {
            log.debug("Embedding call failed", e);
            throw new EmbeddingUnavailableException(
                    "The embedding service is unavailable. Check that Ollama is running and the '"
                            + modelId.substring("ollama:".length()) + "' model is pulled.", e);
        }
    }

    private void checkDimensions(float[] vector) {
        if (vector == null || vector.length != properties.dimensions()) {
            throw new EmbeddingContractException("The embedding model " + modelId + " returned "
                    + (vector == null ? 0 : vector.length) + " dimensions; the index expects "
                    + properties.dimensions() + ".");
        }
    }
}
