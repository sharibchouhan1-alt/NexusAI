package com.nexusai.platform.ai;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Provider adapter for the generation model (Spring AI → local Ollama, e.g. qwen2.5:7b).
 * The only place Spring AI chat types appear. Model, temperature, context window and
 * output cap come from configuration; this adapter adds the per-call JSON schema that
 * constrains the output, and turns every provider failure into
 * {@link GenerationUnavailableException}.
 */
@Service
public class GenerationService {

    private static final Logger log = LoggerFactory.getLogger(GenerationService.class);

    private final ChatModel model;
    private final String modelName;
    private final double temperature;
    private final int seed;
    private final int contextTokens;
    private final int maxTokens;
    private final String keepAlive;

    public GenerationService(ChatModel model,
                             @Value("${spring.ai.ollama.chat.model}") String modelName,
                             @Value("${spring.ai.ollama.chat.temperature}") double temperature,
                             @Value("${spring.ai.ollama.chat.seed}") int seed,
                             @Value("${spring.ai.ollama.chat.num-ctx}") int contextTokens,
                             @Value("${spring.ai.ollama.chat.num-predict}") int maxTokens,
                             @Value("${spring.ai.ollama.chat.keep-alive}") String keepAlive) {
        this.model = model;
        this.modelName = modelName;
        this.temperature = temperature;
        this.seed = seed;
        this.contextTokens = contextTokens;
        this.maxTokens = maxTokens;
        this.keepAlive = keepAlive;
    }

    /** Identifies the generation model; stored with every answer. */
    public String modelId() {
        return "ollama:" + modelName;
    }

    /**
     * Generates a response constrained to {@code jsonSchema} and returns its raw text.
     * Parsing and validating it is the caller's job: model output is never trusted.
     */
    public String generateJson(String systemPrompt, String userPrompt, Map<String, Object> jsonSchema) {
        // Every option is set explicitly: per-call options do not inherit the configured
        // defaults, and an unset model silently falls back to Spring AI's own default.
        OllamaChatOptions options = OllamaChatOptions.builder()
                .model(modelName)
                .temperature(temperature)
                .seed(seed)
                .numCtx(contextTokens)
                .numPredict(maxTokens)
                .keepAlive(keepAlive)
                .format(jsonSchema)
                .build();
        Prompt prompt = new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt)), options);
        ChatResponse response;
        try {
            response = model.call(prompt);
        } catch (RuntimeException e) {
            boolean timeout = isTimeout(e);
            log.warn("Generation call failed ({}): {}", timeout ? "timeout" : "error", rootMessage(e));
            throw new GenerationUnavailableException(timeout
                    ? "The local language model took too long to answer. Try again, or ask a shorter question."
                    : "The local language model is unavailable. Check that Ollama is running and the '"
                            + modelName + "' model is pulled (ollama pull " + modelName + ").",
                    timeout, e);
        }
        String text = response == null || response.getResult() == null || response.getResult().getOutput() == null
                ? null
                : response.getResult().getOutput().getText();
        if (text == null || text.isBlank()) {
            throw new GenerationUnavailableException("The local language model returned an empty response.", false, null);
        }
        return text;
    }

    /**
     * True for read/response timeouts anywhere in the cause chain: JDK types, or by name for
     * client libraries we don't link against (Reactor Netty throws io.netty's
     * ReadTimeoutException). Connect timeouts mean "unreachable", not "too slow".
     */
    static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SocketTimeoutException || t instanceof HttpTimeoutException || t instanceof TimeoutException) {
                return true;
            }
            String type = t.getClass().getSimpleName();
            if (type.contains("Timeout") && !type.contains("Connect")) {
                return true;
            }
            if (t.getMessage() != null && t.getMessage().toLowerCase().contains("timed out")) {
                return true;
            }
        }
        return false;
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) root = root.getCause();
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
