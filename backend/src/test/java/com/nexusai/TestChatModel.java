package com.nexusai;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.client.ResourceAccessException;

/**
 * Test-only stand-in for the local Ollama chat model (the architecture tests services against
 * stubbed provider adapters). The default behaviour is a tiny, deterministic "grounded
 * reader": it picks the source sharing most words with the question and answers with that
 * source's first sentence, quoted exactly and cited. Tests can script other replies
 * (fabricated citations, malformed JSON, ...) or simulate outages and timeouts, failing
 * the way the real Spring AI client does. The real model is exercised by
 * {@code OllamaRagChatTest} and the manual smoke test.
 */
public class TestChatModel implements ChatModel {

    public enum Mode { NORMAL, DOWN, TIMEOUT }

    private static final Pattern SOURCE = Pattern.compile("<source id=\"(\\d+)\"[^>]*>\\n(.*?)\\n</source>", Pattern.DOTALL);
    private static final Pattern QUESTION = Pattern.compile("<question>\\n(.*?)\\n</question>", Pattern.DOTALL);
    private static final Set<String> STOP = Set.of("what", "which", "does", "the", "and", "for", "with", "how", "why",
            "are", "is", "of", "in", "a", "an", "to", "do", "did", "this", "that", "about");

    private volatile Mode mode = Mode.NORMAL;
    private volatile Function<String, String> responder;
    private final AtomicReference<String> lastUserPrompt = new AtomicReference<>();
    private final AtomicReference<String> lastSystemPrompt = new AtomicReference<>();
    private final List<String> calls = new ArrayList<>();

    public void setMode(Mode value) {
        mode = value;
    }

    /** Scripted reply (receives the user prompt); null restores the grounded reader. */
    public void setResponder(Function<String, String> value) {
        responder = value;
    }

    public void reset() {
        mode = Mode.NORMAL;
        responder = null;
        synchronized (calls) {
            calls.clear();
        }
    }

    public String lastUserPrompt() {
        return lastUserPrompt.get();
    }

    public String lastSystemPrompt() {
        return lastSystemPrompt.get();
    }

    public int callCount() {
        synchronized (calls) {
            return calls.size();
        }
    }

    /** Every user prompt received since the last reset, in order. */
    public List<String> prompts() {
        synchronized (calls) {
            return List.copyOf(calls);
        }
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        String system = prompt.getInstructions().stream().filter(m -> m.getMessageType().name().equals("SYSTEM"))
                .map(Message::getText).findFirst().orElse("");
        String user = prompt.getInstructions().stream().filter(m -> m.getMessageType().name().equals("USER"))
                .map(Message::getText).reduce((a, b) -> b).orElse("");
        lastSystemPrompt.set(system);
        lastUserPrompt.set(user);
        synchronized (calls) {
            calls.add(user);
        }
        switch (mode) {
            case DOWN -> throw new ResourceAccessException("I/O error on POST request for \"http://localhost:11434/api/chat\": Connection refused");
            case TIMEOUT -> throw new ResourceAccessException("I/O error on POST request: Read timed out",
                    new SocketTimeoutException("Read timed out"));
            default -> { }
        }
        String reply = responder != null ? responder.apply(user) : groundedReader(user);
        return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
    }

    static String groundedReader(String userPrompt) {
        Matcher q = QUESTION.matcher(userPrompt);
        Set<String> questionWords = q.find() ? words(q.group(1)) : Set.of();
        int bestId = -1;
        long bestOverlap = 0;
        String bestText = null;
        Matcher s = SOURCE.matcher(userPrompt);
        while (s.find()) {
            long overlap = words(s.group(2)).stream().filter(questionWords::contains).count();
            if (overlap > bestOverlap) {
                bestOverlap = overlap;
                bestId = Integer.parseInt(s.group(1));
                bestText = s.group(2);
            }
        }
        if (bestId < 0 || bestOverlap < 2) {
            return "{\"answerable\": false, \"answer\": \"The sources do not say.\", \"citations\": []}";
        }
        String sentence = firstSentence(bestText);
        return "{\"answerable\": true, \"answer\": " + quote(sentence + " [" + bestId + "]")
                + ", \"citations\": [{\"source\": " + bestId + ", \"quote\": " + quote(sentence) + "}]}";
    }

    private static String firstSentence(String text) {
        String flat = text.replaceAll("\\s+", " ").strip();
        int end = flat.indexOf(". ");
        return end > 0 ? flat.substring(0, end + 1) : flat;
    }

    private static Set<String> words(String text) {
        return List.of(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")).stream()
                .filter(w -> w.length() > 2 && !STOP.contains(w)).collect(Collectors.toSet());
    }

    public static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
