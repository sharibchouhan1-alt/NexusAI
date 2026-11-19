package com.nexusai.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nexusai.ApiTestSupport;
import com.nexusai.TestChatModel;
import com.nexusai.TestEmbeddingModel;
import com.nexusai.TestFiles;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

class ResearchApiIntegrationTest extends ApiTestSupport {

    private static final String PHOTOSYNTHESIS =
            "Photosynthesis converts light energy into chemical energy. Chlorophyll in leaves absorbs red and blue light.";
    private static final String STARS =
            "Stars fuse hydrogen into helium in their cores. Supernovae scatter heavy elements across galaxies.";
    private static final Set<String> TERMINAL = Set.of("COMPLETED", "INSUFFICIENT_EVIDENCE", "FAILED", "CANCELLED");

    @Autowired private TestChatModel chatModel;
    @Autowired private TestEmbeddingModel embeddingModel;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ResearchOrchestrator orchestrator;
    @Autowired private ResearchStore store;

    private ResearchScript script;

    @BeforeEach
    void installScript() {
        chatModel.reset();
        script = new ResearchScript();
        chatModel.setResponder(script);
    }

    @AfterEach
    void restore() {
        chatModel.reset();
        embeddingModel.setAvailable(true);
    }

    // --- the full workflow ---

    @Test
    void plansInvokesToolsCollectsProvenanceAndProducesAVerifiedReport() throws Exception {
        String ws = createWorkspace("Research");
        String photo = indexed(ws, "photosynthesis.txt", PHOTOSYNTHESIS);
        String astro = indexedPdf(ws, "astro.pdf", "Introduction to the night sky.", STARS);

        String started = mvc.perform(post("/api/workspaces/{ws}/research", ws).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objective\": \"How do plants and stars produce energy?\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.objective").value("How do plants and stars produce energy?"))
                .andExpect(jsonPath("$.limits.maxToolCalls").value(10))
                .andReturn().getResponse().getContentAsString();
        String run = JsonPath.read(started, "$.id");

        String body = awaitRun(ws, run);
        assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("COMPLETED");
        assertThat((String) JsonPath.read(body, "$.planOrigin")).isEqualTo("MODEL");
        assertThat((String) JsonPath.read(body, "$.model")).isEqualTo("ollama:qwen2.5:7b");

        // Plan persisted; every step executed.
        assertThat(JsonPath.<List<String>>read(body, "$.steps[*].title"))
                .containsExactly("Find the photosynthesis mechanism", "Find the energy source of stars");
        assertThat(JsonPath.<List<String>>read(body, "$.steps[*].status")).containsOnly("COMPLETED");
        assertThat(JsonPath.<List<List<String>>>read(body, "$.steps[*].plannedQueries").getFirst())
                .containsExactly("photosynthesis light energy");

        // The agent really invoked both tools, in order, with audited arguments.
        assertThat(JsonPath.<List<String>>read(body, "$.steps[0].toolCalls[*].tool"))
                .containsExactly("search_knowledge", "retrieve_source");
        assertThat(JsonPath.<List<String>>read(body, "$.steps[*].toolCalls[*].status")).containsOnly("SUCCEEDED");
        assertThat((String) JsonPath.read(body, "$.steps[0].toolCalls[0].arguments")).contains("photosynthesis light energy");
        assertThat((String) JsonPath.read(body, "$.steps[0].toolCalls[0].resultSummary")).startsWith("Found");
        assertThat(JsonPath.<List<Integer>>read(body, "$.steps[*].toolCalls[*].seq")).containsExactly(1, 2, 3, 4);
        int toolCalls = JsonPath.read(body, "$.toolCallCount");
        assertThat(toolCalls).isEqualTo(4);

        // Sources carry real provenance that matches the database.
        List<Map<String, Object>> sources = JsonPath.read(body, "$.sources");
        assertThat(sources).isNotEmpty();
        for (Map<String, Object> s : sources) {
            String chunkId = (String) s.get("chunkId");
            Map<String, Object> chunk = jdbc.queryForMap(
                    "SELECT document_id::text AS doc, workspace_id::text AS ws, content, page_number FROM document_chunks WHERE id = ?::uuid", chunkId);
            assertThat(chunk.get("doc")).isEqualTo(s.get("documentId"));
            assertThat(chunk.get("ws")).isEqualTo(ws);
            assertThat(chunk.get("content")).isEqualTo(s.get("content"));
            assertThat(chunk.get("page_number")).isEqualTo(s.get("pageNumber"));
        }
        assertThat(sources).extracting(s -> s.get("documentId")).contains(photo, astro);
        assertThat(sources).filteredOn(s -> astro.equals(s.get("documentId"))).extracting(s -> s.get("pageNumber")).contains(2);
        assertThat(JsonPath.<List<String>>read(body, "$.sources[*].handle")).startsWith("P1", "P2");

        // The report is citation-backed, and each citation points at a gathered source.
        assertThat((String) JsonPath.read(body, "$.report.title")).isEqualTo("Research findings");
        assertThat((String) JsonPath.read(body, "$.report.text")).contains("[1]").contains("## Key finding");
        List<Map<String, Object>> citations = JsonPath.read(body, "$.report.citations");
        assertThat(citations).isNotEmpty();
        Set<Object> gatheredChunks = Set.copyOf(sources.stream().map(s -> s.get("chunkId")).toList());
        for (Map<String, Object> c : citations) {
            assertThat(gatheredChunks).contains(c.get("chunkId"));
            assertThat(((String) c.get("passage")).replaceAll("\\s+", " ")).contains(((String) c.get("quote")).replaceAll("\\s+", " "));
        }
        assertThat(rows("citations", "research_run_id", run)).isEqualTo(citations.size());
        assertThat(script.plannerCalls.get()).isEqualTo(1);
        assertThat(script.synthesisCalls.get()).isEqualTo(1);

        mvc.perform(get("/api/workspaces/{ws}/research", ws))
                .andExpect(jsonPath("$.items[0].id").value(run))
                .andExpect(jsonPath("$.items[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.items[0].reportTitle").value("Research findings"));
    }

    // --- workspace isolation and argument safety ---

    @Test
    void researchNeverLeavesItsWorkspace() throws Exception {
        String wsA = createWorkspace("A");
        String wsB = createWorkspace("B");
        String notesA = indexed(wsA, "notes.txt", "The confidential plan is to grow the analytics dashboard. No merger is planned.");
        indexed(wsB, "secret.txt", "Confidential merger plan: acquire Initech in March for twelve million.");
        // The model tries to point the tool at the other workspace through its arguments.
        script.agentOverride = prompt -> prompt.contains("No actions taken yet")
                ? "{\"action\": \"search_knowledge\", \"arguments\": {\"query\": \"confidential merger plan Initech\", \"workspaceId\": \"" + wsB + "\"}, \"reason\": \"x\"}"
                : "{\"action\": \"finish_step\", \"arguments\": {}, \"reason\": \"done\"}";

        String run = start(wsA, "What is the confidential merger plan with Initech?");
        String body = awaitRun(wsA, run);

        assertThat(JsonPath.<List<String>>read(body, "$.sources[*].documentId")).containsOnly(notesA);
        assertThat(body).doesNotContain("Initech in March");
        assertThat(String.join("\n", chatModel.prompts())).doesNotContain("twelve million");

        mvc.perform(get("/api/workspaces/{ws}/research/{r}", wsB, run)).andExpect(status().isNotFound());
        mvc.perform(post("/api/workspaces/{ws}/research/{r}/cancel", wsB, run)).andExpect(status().isNotFound());
        mvc.perform(post("/api/workspaces/{ws}/research/{r}/resume", wsB, run)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/workspaces/{ws}/research/{r}", wsB, run)).andExpect(status().isNotFound());
        mvc.perform(get("/api/workspaces/{ws}/research", wsB)).andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void handlesFromElsewhereAndUnsafeActionsAreRejectedAndAudited() throws Exception {
        String ws = createWorkspace("Safety");
        indexed(ws, "photosynthesis.txt", PHOTOSYNTHESIS);
        script.planJson = "{\"steps\": [{\"title\": \"Only step\", \"goal\": \"photosynthesis\", \"queries\": [\"photosynthesis light energy\"]}]}";
        List<String> actions = List.of(
                "{\"action\": \"retrieve_source\", \"arguments\": {\"passage\": \"P99\"}, \"reason\": \"guess\"}",
                "{\"action\": \"execute_code\", \"arguments\": {\"code\": \"Runtime.getRuntime().exec('rm -rf /')\"}, \"reason\": \"evil\"}",
                "{\"action\": \"search_knowledge\", \"arguments\": {\"query\": \"photosynthesis light energy\", \"limit\": 999}, \"reason\": \"too many\"}",
                "{\"action\": \"search_knowledge\", \"arguments\": {\"query\": \"photosynthesis light energy\", \"limit\": 2}, \"reason\": \"ok\"}");
        java.util.concurrent.atomic.AtomicInteger i = new java.util.concurrent.atomic.AtomicInteger();
        script.agentOverride = prompt -> actions.get(Math.min(i.getAndIncrement(), actions.size() - 1));

        String body = awaitRun(ws, start(ws, "How does photosynthesis convert light energy?"));

        List<Map<String, Object>> calls = JsonPath.read(body, "$.steps[0].toolCalls");
        assertThat(calls).extracting(c -> c.get("status")).startsWith("REJECTED", "REJECTED", "REJECTED");
        assertThat((String) calls.get(0).get("error")).contains("Unknown passage P99");
        assertThat((String) calls.get(1).get("error")).contains("Unknown action 'execute_code'");
        assertThat((String) calls.get(2).get("error")).contains("limit");
        assertThat(calls).extracting(c -> c.get("status")).contains("SUCCEEDED");
        assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("COMPLETED");
    }

    // --- evidence outcomes ---

    @Test
    void noEvidenceMeansInsufficientEvidenceWithoutSynthesis() throws Exception {
        String ws = createWorkspace("Empty");
        String body = awaitRun(ws, start(ws, "What does the quarterly report say?"));
        assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("INSUFFICIENT_EVIDENCE");
        assertThat((String) JsonPath.read(body, "$.statusDetail")).contains("no passages");
        assertThat((Object) JsonPath.read(body, "$.report")).isNull();
        assertThat(script.synthesisCalls.get()).isZero();
        assertThat(JsonPath.<List<String>>read(body, "$.steps[*].toolCalls[*].status")).contains("SUCCEEDED");
    }

    @Test
    void anUnverifiableReportIsNeverShown() throws Exception {
        String ws = createWorkspace("Unverifiable");
        indexed(ws, "stars.txt", STARS);
        script.synthesisOverride = prompt -> """
                {"sufficient": true, "title": "Cheese", "summary": "Stars are made of cheese [1].",
                 "sections": [{"heading": "Claims", "content": "NASA confirmed it [7]."}],
                 "citations": [{"source": 1, "quote": "Stars are made of green cheese"}, {"source": 7, "quote": "NASA confirmed it"}],
                 "gaps": []}""";

        String body = awaitRun(ws, start(ws, "What powers stars and what are they made of?"));
        assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("INSUFFICIENT_EVIDENCE");
        assertThat((String) JsonPath.read(body, "$.statusDetail")).contains("could not be verified");
        assertThat((Object) JsonPath.read(body, "$.report")).isNull();
        assertThat(body).doesNotContain("cheese");
    }

    @Test
    void partlyCoveredObjectivesProduceAPartialReportWithGaps() throws Exception {
        String ws = createWorkspace("Partial");
        indexed(ws, "stars.txt", STARS);
        script.synthesisOverride = prompt -> ResearchScript.groundedReport(prompt)
                .replace("\"sufficient\": true", "\"sufficient\": false")
                .replace("\"gaps\": []", "\"gaps\": [\"How planets form\"]");

        String body = awaitRun(ws, start(ws, "How do stars and planets form?"));
        assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("COMPLETED");
        assertThat((Boolean) JsonPath.read(body, "$.report.partial")).isTrue();
        assertThat(JsonPath.<List<String>>read(body, "$.gaps")).containsExactly("How planets form");
    }

    // --- failures and recovery ---

    @Test
    void modelOutageDuringPlanningFailsAndResumeCompletes() throws Exception {
        String ws = createWorkspace("Outage");
        indexed(ws, "photosynthesis.txt", PHOTOSYNTHESIS);
        AtomicBoolean down = new AtomicBoolean(true);
        script.failWhen = p -> down.get();

        String run = start(ws, "How does photosynthesis convert light energy?");
        String failed = awaitRun(ws, run);
        assertThat((String) JsonPath.read(failed, "$.status")).isEqualTo("FAILED");
        assertThat((String) JsonPath.read(failed, "$.statusDetail")).contains("ollama pull").contains("Resume the run");
        assertThat(JsonPath.<List<Object>>read(failed, "$.steps")).isEmpty();

        down.set(false);
        mvc.perform(post("/api/workspaces/{ws}/research/{r}/resume", ws, run))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.resumeCount").value(1));
        String body = awaitRun(ws, run);
        assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("COMPLETED");
    }

    @Test
    void anOutageMidRunResumesFromTheUnfinishedStepWithoutRepeatingWork() throws Exception {
        String ws = createWorkspace("Mid-run");
        indexed(ws, "photosynthesis.txt", PHOTOSYNTHESIS);
        indexed(ws, "stars.txt", STARS);
        AtomicBoolean down = new AtomicBoolean(true);
        script.failWhen = p -> down.get() && p.contains("<step number=\"2\"");

        String run = start(ws, "How do plants and stars produce energy?");
        String failed = awaitRun(ws, run);
        assertThat((String) JsonPath.read(failed, "$.status")).isEqualTo("FAILED");
        assertThat(JsonPath.<List<String>>read(failed, "$.steps[*].status")).containsExactly("COMPLETED", "RUNNING");
        int stepOneCalls = JsonPath.<List<Object>>read(failed, "$.steps[0].toolCalls").size();
        int sourcesBefore = JsonPath.read(failed, "$.sourceCount");
        assertThat(sourcesBefore).isPositive();

        down.set(false);
        mvc.perform(post("/api/workspaces/{ws}/research/{r}/resume", ws, run)).andExpect(status().isAccepted());
        String body = awaitRun(ws, run);

        assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("COMPLETED");
        assertThat(JsonPath.<List<String>>read(body, "$.steps[*].status")).containsOnly("COMPLETED");
        assertThat(JsonPath.<List<Object>>read(body, "$.steps[0].toolCalls")).hasSize(stepOneCalls); // not re-run
        assertThat(script.plannerCalls.get()).isEqualTo(1); // the plan was reused
        assertThat((Integer) JsonPath.read(body, "$.sourceCount")).isGreaterThanOrEqualTo(sourcesBefore);
    }

    @Test
    void toolFailuresAreRecordedAndFailTheRun() throws Exception {
        String ws = createWorkspace("Tools down");
        indexed(ws, "photosynthesis.txt", PHOTOSYNTHESIS);
        embeddingModel.setAvailable(false);

        String body = awaitRun(ws, start(ws, "How does photosynthesis convert light energy?"));

        assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("FAILED");
        assertThat((String) JsonPath.read(body, "$.statusDetail")).contains("research tools failed").contains("embedding service");
        assertThat(JsonPath.<List<String>>read(body, "$.steps[*].toolCalls[*].status")).containsOnly("FAILED");
        assertThat(JsonPath.<List<String>>read(body, "$.steps[*].status")).containsOnly("FAILED");
    }

    @Test
    void executionIsBoundedByActionsAndTheRunWideToolBudget() throws Exception {
        String ws = createWorkspace("Bounded");
        indexed(ws, "photosynthesis.txt", PHOTOSYNTHESIS);
        script.planJson = """
                {"steps": [
                  {"title": "One", "goal": "g1", "queries": ["photosynthesis"]},
                  {"title": "Two", "goal": "g2", "queries": ["chlorophyll"]},
                  {"title": "Three", "goal": "g3", "queries": ["light"]},
                  {"title": "Four", "goal": "g4", "queries": ["energy"]}
                ]}""";
        java.util.concurrent.atomic.AtomicInteger n = new java.util.concurrent.atomic.AtomicInteger();
        // A model that never finishes: always a new search.
        script.agentOverride = prompt -> "{\"action\": \"search_knowledge\", \"arguments\": {\"query\": \"photosynthesis variant "
                + n.incrementAndGet() + "\"}, \"reason\": \"keep going\"}";

        String body = awaitRun(ws, start(ws, "Exhaustively research photosynthesis"));

        assertThat((Integer) JsonPath.read(body, "$.toolCallCount")).isEqualTo(10); // test budget
        assertThat(JsonPath.<List<Integer>>read(body, "$.steps[*].actionCount")).containsExactly(4, 4, 2, 0);
        assertThat(JsonPath.<List<String>>read(body, "$.steps[*].status")).containsExactly("COMPLETED", "COMPLETED", "COMPLETED", "SKIPPED");
        assertThat((String) JsonPath.read(body, "$.steps[0].statusDetail")).contains("action limit");
        assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("COMPLETED");
    }

    @Test
    void runningAndQueuedRunsCanBeCancelled() throws Exception {
        String ws = createWorkspace("Cancel");
        indexed(ws, "photosynthesis.txt", PHOTOSYNTHESIS);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        script.agentOverride = prompt -> {
            entered.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return ResearchScript.defaultAgent(prompt);
        };

        String running = start(ws, "How does photosynthesis convert light energy?");
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        String queued = start(ws, "A second objective waiting in the queue");   // one worker thread: stays QUEUED
        mvc.perform(get("/api/workspaces/{ws}/research/{r}", ws, queued)).andExpect(jsonPath("$.status").value("QUEUED"));

        mvc.perform(post("/api/workspaces/{ws}/research/{r}/cancel", ws, queued))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/api/workspaces/{ws}/research/{r}/cancel", ws, running))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.cancelRequested").value(true));
        mvc.perform(delete("/api/workspaces/{ws}/research/{r}", ws, running)).andExpect(status().isConflict());
        release.countDown();

        String body = awaitRun(ws, running);
        assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("CANCELLED");
        assertThat(JsonPath.<List<String>>read(body, "$.steps[*].status")).doesNotContain("COMPLETED");
        assertThat(script.synthesisCalls.get()).isZero();
        assertThat((String) JsonPath.read(awaitRun(ws, queued), "$.status")).isEqualTo("CANCELLED");
        mvc.perform(post("/api/workspaces/{ws}/research/{r}/cancel", ws, running)).andExpect(status().isConflict());
    }

    @Test
    void runsInterruptedByARestartResumeOrFailWithinTheBound() throws Exception {
        String ws = createWorkspace("Restart");
        indexed(ws, "photosynthesis.txt", PHOTOSYNTHESIS);
        UUID interrupted = store.create(UUID.fromString(ws), "How does photosynthesis convert light energy?");
        jdbc.update("UPDATE research_runs SET status = 'RUNNING' WHERE id = ?", interrupted);
        UUID exhausted = store.create(UUID.fromString(ws), "Another interrupted objective");
        jdbc.update("UPDATE research_runs SET status = 'PLANNING', resume_count = 2 WHERE id = ?", exhausted);

        orchestrator.recoverInterruptedRuns();

        String body = awaitRun(ws, interrupted.toString());
        assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("COMPLETED");
        assertThat((Integer) JsonPath.read(body, "$.resumeCount")).isEqualTo(1);
        String gaveUp = awaitRun(ws, exhausted.toString());
        assertThat((String) JsonPath.read(gaveUp, "$.status")).isEqualTo("FAILED");
        assertThat((String) JsonPath.read(gaveUp, "$.statusDetail")).contains("too many times");
    }

    // --- validation and deletion ---

    @Test
    void invalidRequestsAreRejected() throws Exception {
        String ws = createWorkspace("Validation");
        startRequest(ws, "   ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("objective"));
        startRequest(ws, "too short").andExpect(status().isBadRequest());
        startRequest(ws, "x".repeat(2001)).andExpect(status().isBadRequest());
        startRequest(UUID.randomUUID().toString(), "A perfectly fine objective").andExpect(status().isNotFound());
        mvc.perform(get("/api/workspaces/{ws}/research/{r}", ws, UUID.randomUUID())).andExpect(status().isNotFound());
        String done = awaitRun(ws, start(ws, "An objective with no documents"));
        String run = JsonPath.read(done, "$.id");
        mvc.perform(post("/api/workspaces/{ws}/research/{r}/resume", ws, run))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("failed or cancelled")));
    }

    @Test
    void deletingARunRemovesItsWholeTrail() throws Exception {
        String ws = createWorkspace("Delete");
        indexed(ws, "photosynthesis.txt", PHOTOSYNTHESIS);
        String run = start(ws, "How does photosynthesis convert light energy?");
        assertThat((String) JsonPath.read(awaitRun(ws, run), "$.status")).isEqualTo("COMPLETED");
        assertThat(rows("research_tool_calls", "run_id", run)).isPositive();

        mvc.perform(delete("/api/workspaces/{ws}/research/{r}", ws, run)).andExpect(status().isNoContent());
        for (String table : List.of("research_steps", "research_tool_calls", "research_sources")) {
            assertThat(rows(table, "run_id", run)).as(table).isZero();
        }
        assertThat(rows("citations", "research_run_id", run)).isZero();
        mvc.perform(get("/api/workspaces/{ws}/research/{r}", ws, run)).andExpect(status().isNotFound());
    }

    // --- helpers ---

    private String indexed(String ws, String filename, String text) throws Exception {
        String id = uploadText(ws, filename, text);
        awaitIndex(ws, id, "INDEXED");
        return id;
    }

    private String indexedPdf(String ws, String filename, String... pages) throws Exception {
        String id = JsonPath.read(mvc.perform(multipart("/api/workspaces/{ws}/documents", ws)
                        .file(new MockMultipartFile("file", filename, "application/pdf", TestFiles.pdf(pages))))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString(), "$.id");
        awaitIndex(ws, id, "INDEXED");
        return id;
    }

    private ResultActions startRequest(String ws, String objective) throws Exception {
        return mvc.perform(post("/api/workspaces/{ws}/research", ws).contentType(MediaType.APPLICATION_JSON)
                .content("{\"objective\": " + TestChatModel.quote(objective) + "}"));
    }

    private String start(String ws, String objective) throws Exception {
        return JsonPath.read(startRequest(ws, objective).andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String awaitRun(String ws, String run) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (true) {
            String body = mvc.perform(get("/api/workspaces/{ws}/research/{r}", ws, run))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            if (TERMINAL.contains((String) JsonPath.read(body, "$.status"))) return body;
            if (System.nanoTime() > deadline) throw new AssertionError("Research did not finish: " + body);
            Thread.sleep(50);
        }
    }

    private int rows(String table, String column, String id) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + column + " = ?::uuid", Integer.class, id);
    }
}
