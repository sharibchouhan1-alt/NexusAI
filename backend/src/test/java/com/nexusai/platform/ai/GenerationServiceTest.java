package com.nexusai.platform.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.web.client.ResourceAccessException;

class GenerationServiceTest {

    /** Same simple name as Netty's io.netty.handler.timeout.ReadTimeoutException (seen with the real client). */
    static class ReadTimeoutException extends RuntimeException {}

    /** Same simple name as Netty's connect timeout: the server is unreachable, not slow. */
    static class ConnectTimeoutException extends RuntimeException {}

    private static GenerationService service(ChatModel model) {
        return new GenerationService(model, "qwen2.5:7b", 0.1, 42, 8192, 800, "10m");
    }

    @Test
    void everyConfiguredOptionIsSentExplicitlyWithTheSchema() {
        AtomicReference<Prompt> seen = new AtomicReference<>();
        ChatModel model = prompt -> {
            seen.set(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("{\"answerable\":false}"))));
        };
        Map<String, Object> schema = Map.of("type", "object");

        assertThat(service(model).generateJson("system", "user", schema)).isEqualTo("{\"answerable\":false}");
        OllamaChatOptions options = (OllamaChatOptions) seen.get().getOptions();
        assertThat(options.getModel()).isEqualTo("qwen2.5:7b"); // never Spring AI's default model
        assertThat(options.getTemperature()).isEqualTo(0.1);
        assertThat(options.getSeed()).isEqualTo(42);
        assertThat(options.getNumCtx()).isEqualTo(8192);
        assertThat(options.getNumPredict()).isEqualTo(800);
        assertThat(options.getFormat()).isEqualTo(schema);
        assertThat(service(model).modelId()).isEqualTo("ollama:qwen2.5:7b");
    }

    @Test
    void readTimeoutsAreReportedAsTimeouts() {
        assertThat(GenerationService.isTimeout(new RuntimeException(new ReadTimeoutException()))).isTrue();
        assertThat(GenerationService.isTimeout(new ResourceAccessException("x", new SocketTimeoutException("Read timed out")))).isTrue();
        assertThat(GenerationService.isTimeout(new RuntimeException(new ConnectTimeoutException()))).isFalse();
        assertThat(GenerationService.isTimeout(new ResourceAccessException("Connection refused"))).isFalse();

        assertThatThrownBy(() -> service(p -> { throw new RuntimeException(new ReadTimeoutException()); })
                .generateJson("s", "u", Map.of()))
                .isInstanceOf(GenerationUnavailableException.class)
                .hasMessageContaining("took too long")
                .matches(e -> ((GenerationUnavailableException) e).isTimeout());
    }

    @Test
    void unreachableOrMissingModelsAreReportedAsUnavailable() {
        assertThatThrownBy(() -> service(p -> { throw new ResourceAccessException("Connection refused"); })
                .generateJson("s", "u", Map.of()))
                .isInstanceOf(GenerationUnavailableException.class)
                .hasMessageContaining("ollama pull qwen2.5:7b")
                .matches(e -> !((GenerationUnavailableException) e).isTimeout());
    }

    @Test
    void emptyResponsesAreFailures() {
        assertThatThrownBy(() -> service(p -> new ChatResponse(List.of(new Generation(new AssistantMessage("  ")))))
                .generateJson("s", "u", Map.of()))
                .isInstanceOf(GenerationUnavailableException.class)
                .hasMessageContaining("empty response");
    }
}
