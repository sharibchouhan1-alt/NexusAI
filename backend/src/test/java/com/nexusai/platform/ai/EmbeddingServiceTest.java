package com.nexusai.platform.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexusai.TestEmbeddingModel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

class EmbeddingServiceTest {

    private static final EmbeddingProperties PROPERTIES =
            new EmbeddingProperties(768, 32, "search_document: ", "search_query: ", Duration.ofSeconds(30));

    @Test
    void appliesTaskPrefixesAndIdentifiesTheModel() {
        List<String> seen = new ArrayList<>();
        EmbeddingModel recording = new TestEmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                seen.addAll(request.getInstructions());
                return super.call(request);
            }
        };
        EmbeddingService service = new EmbeddingService(recording, PROPERTIES, "nomic-embed-text");

        assertThat(service.embedDocuments(List.of("alpha", "beta"))).hasSize(2).allSatisfy(v -> assertThat(v).hasSize(768));
        assertThat(service.embedQuery("gamma")).hasSize(768);
        assertThat(seen).containsExactly("search_document: alpha", "search_document: beta", "search_query: gamma");
        assertThat(service.modelId()).isEqualTo("ollama:nomic-embed-text");
        assertThat(service.embedDocuments(List.of())).isEmpty();
    }

    @Test
    void providerFailuresBecomeUnavailableWithAUserFacingMessage() {
        TestEmbeddingModel model = new TestEmbeddingModel();
        model.setAvailable(false);
        EmbeddingService service = new EmbeddingService(model, PROPERTIES, "nomic-embed-text");

        assertThatThrownBy(() -> service.embedQuery("x"))
                .isInstanceOf(EmbeddingUnavailableException.class)
                .hasMessageContaining("Ollama is running")
                .hasMessageNotContaining("Connection refused") // provider details stay in the cause
                .hasRootCauseMessage("I/O error on POST request for \"http://localhost:11434/api/embed\": Connection refused");
        assertThat(service.isAvailable()).isFalse();

        model.setAvailable(true);
        assertThat(service.isAvailable()).isTrue();
    }

    @Test
    void wrongDimensionsViolateTheIndexContract() {
        TestEmbeddingModel model = new TestEmbeddingModel();
        model.setDimensions(384);
        EmbeddingService service = new EmbeddingService(model, PROPERTIES, "nomic-embed-text");

        assertThatThrownBy(() -> service.embedDocuments(List.of("x")))
                .isInstanceOf(EmbeddingContractException.class)
                .hasMessageContaining("384")
                .hasMessageContaining("768");
    }

    @Test
    void wrongVectorCountViolatesTheContract() {
        EmbeddingModel dropsOne = new TestEmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                EmbeddingResponse full = super.call(request);
                return new EmbeddingResponse(full.getResults().subList(0, 1));
            }

            @Override
            public float[] embed(Document document) {
                throw new UnsupportedOperationException();
            }
        };
        EmbeddingService service = new EmbeddingService(dropsOne, PROPERTIES, "nomic-embed-text");
        assertThatThrownBy(() -> service.embedDocuments(List.of("a", "b")))
                .isInstanceOf(EmbeddingContractException.class);
    }
}
