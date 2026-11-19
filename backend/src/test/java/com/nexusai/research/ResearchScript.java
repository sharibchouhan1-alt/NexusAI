package com.nexusai.research;

import com.nexusai.TestChatModel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.client.ResourceAccessException;

/**
 * Test-only behaviour for the three research prompts (plan / agent action / synthesis),
 * plugged into {@link TestChatModel}. The default is a small but genuine agent: it searches
 * the step's first suggested query, reads the first passage it found in context, then
 * finishes; the report quotes the first sentence of the strongest source. Every tool call
 * still goes through the real registry, search, database and citation verification.
 */
class ResearchScript implements Function<String, String> {

    private static final Pattern SUGGESTED = Pattern.compile("Suggested queries:\\n- (.+)");
    private static final Pattern PASSAGE = Pattern.compile("<passage id=\"(P\\d+)\"");
    private static final Pattern SOURCE = Pattern.compile("<source id=\"(\\d+)\"[^>]*>\\n(.*?)\\n</source>", Pattern.DOTALL);

    String planJson = """
            {"steps": [
              {"title": "Find the photosynthesis mechanism", "goal": "How plants convert light", "queries": ["photosynthesis light energy"]},
              {"title": "Find the energy source of stars", "goal": "What powers stars", "queries": ["stars fuse hydrogen"]}
            ]}""";

    Function<String, String> plannerOverride;
    Function<String, String> agentOverride;
    Function<String, String> synthesisOverride;
    /** Throws "connection refused" (like the real client) for prompts matching this. */
    Predicate<String> failWhen = p -> false;
    final AtomicInteger plannerCalls = new AtomicInteger();
    final AtomicInteger agentCalls = new AtomicInteger();
    final AtomicInteger synthesisCalls = new AtomicInteger();
    final List<String> agentPrompts = new ArrayList<>();

    @Override
    public String apply(String prompt) {
        if (failWhen.test(prompt)) {
            throw new ResourceAccessException("I/O error on POST request for \"http://localhost:11434/api/chat\": Connection refused");
        }
        if (prompt.contains("Write the research plan.")) {
            plannerCalls.incrementAndGet();
            return plannerOverride != null ? plannerOverride.apply(prompt) : planJson;
        }
        if (prompt.contains("<step number=")) {
            agentCalls.incrementAndGet();
            synchronized (agentPrompts) {
                agentPrompts.add(prompt);
            }
            return agentOverride != null ? agentOverride.apply(prompt) : defaultAgent(prompt);
        }
        if (prompt.contains("Write the research report.")) {
            synthesisCalls.incrementAndGet();
            return synthesisOverride != null ? synthesisOverride.apply(prompt) : groundedReport(prompt);
        }
        throw new IllegalStateException("Unexpected prompt: " + prompt.substring(0, Math.min(200, prompt.length())));
    }

    static String defaultAgent(String prompt) {
        if (prompt.contains("No actions taken yet in this step.")) {
            Matcher m = SUGGESTED.matcher(prompt);
            String query = m.find() ? m.group(1).strip() : "research";
            return "{\"action\": \"search_knowledge\", \"arguments\": {\"query\": " + TestChatModel.quote(query)
                    + ", \"limit\": 3}, \"reason\": \"Search the suggested query.\"}";
        }
        String observations = prompt.substring(prompt.indexOf("<observations>"));
        Matcher passage = PASSAGE.matcher(observations);
        if (!observations.contains("retrieve_source") && passage.find()) {
            return "{\"action\": \"retrieve_source\", \"arguments\": {\"passage\": \"" + passage.group(1)
                    + "\", \"context\": 1}, \"reason\": \"Read the best passage in context.\"}";
        }
        return "{\"action\": \"finish_step\", \"arguments\": {}, \"reason\": \"The step's goal is covered.\"}";
    }

    static String groundedReport(String prompt) {
        Matcher s = SOURCE.matcher(prompt);
        if (!s.find()) {
            return "{\"sufficient\": false, \"title\": \"\", \"summary\": \"\", \"sections\": [], \"citations\": [], \"gaps\": [\"Everything\"]}";
        }
        int id = Integer.parseInt(s.group(1));
        String flat = s.group(2).replaceAll("\\s+", " ").strip();
        int end = flat.indexOf(". ");
        String sentence = end > 0 ? flat.substring(0, end + 1) : flat;
        return "{\"sufficient\": true, \"title\": \"Research findings\", \"summary\": " + TestChatModel.quote(sentence + " [" + id + "]")
                + ", \"sections\": [{\"heading\": \"Key finding\", \"content\": " + TestChatModel.quote(sentence + " [" + id + "]") + "}]"
                + ", \"citations\": [{\"source\": " + id + ", \"quote\": " + TestChatModel.quote(sentence) + "}], \"gaps\": []}";
    }
}
