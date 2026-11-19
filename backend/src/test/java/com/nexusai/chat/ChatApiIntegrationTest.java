package com.nexusai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nexusai.ApiTestSupport;
import com.nexusai.TestChatModel;
import com.nexusai.TestEmbeddingModel;
import com.nexusai.TestFiles;
import com.nexusai.rag.RagAnswer;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

class ChatApiIntegrationTest extends ApiTestSupport {

    private static final String PHOTOSYNTHESIS =
            "Photosynthesis converts light energy into chemical energy. Chlorophyll in leaves absorbs red and blue light.";
    private static final String STARS =
            "Stars fuse hydrogen into helium in their cores. Supernovae scatter heavy elements across galaxies.";

    @Autowired private TestChatModel chatModel;
    @Autowired private TestEmbeddingModel embeddingModel;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ChatService chatService;

    @AfterEach
    void restoreModels() {
        chatModel.reset();
        embeddingModel.setAvailable(true);
    }

    // --- grounded answers, persistence, provenance ---

    @Test
    void groundedAnswerWithVerifiableCitationIsPersisted() throws Exception {
        String ws = createWorkspace("Chat");
        String photo = indexed(ws, "photosynthesis.txt", PHOTOSYNTHESIS);
        indexed(ws, "stars.txt", STARS);
        String chunkId = jdbc.queryForObject("SELECT id::text FROM document_chunks WHERE document_id = ?::uuid", String.class, photo);

        String conv = createConversation(ws);
        mvc.perform(get("/api/workspaces/{ws}/conversations/{c}", ws, conv))
                .andExpect(jsonPath("$.title").value("New chat"))
                .andExpect(jsonPath("$.messages.length()").value(0));

        ask(ws, conv, "How does photosynthesis convert light energy?")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.question.role").value("USER"))
                .andExpect(jsonPath("$.question.status").value("COMPLETE"))
                .andExpect(jsonPath("$.question.content").value("How does photosynthesis convert light energy?"))
                .andExpect(jsonPath("$.answer.role").value("ASSISTANT"))
                .andExpect(jsonPath("$.answer.status").value("COMPLETE"))
                .andExpect(jsonPath("$.answer.content").value("Photosynthesis converts light energy into chemical energy. [1]"))
                .andExpect(jsonPath("$.answer.model").value("ollama:qwen2.5:7b"))
                // The unrelated stars passage falls below the relevance threshold: not evidence.
                .andExpect(jsonPath("$.answer.retrievedCount").value(1))
                .andExpect(jsonPath("$.answer.citations.length()").value(1))
                .andExpect(jsonPath("$.answer.citations[0].ordinal").value(1))
                .andExpect(jsonPath("$.answer.citations[0].documentId").value(photo))
                .andExpect(jsonPath("$.answer.citations[0].chunkId").value(chunkId))
                .andExpect(jsonPath("$.answer.citations[0].documentTitle").value("photosynthesis"))
                .andExpect(jsonPath("$.answer.citations[0].pageNumber").isEmpty())
                .andExpect(jsonPath("$.answer.citations[0].quote").value("Photosynthesis converts light energy into chemical energy."))
                .andExpect(jsonPath("$.answer.citations[0].passage").value(PHOTOSYNTHESIS));

        // Persisted in order, with citations, and the conversation is titled by its first question.
        mvc.perform(get("/api/workspaces/{ws}/conversations/{c}", ws, conv))
                .andExpect(jsonPath("$.title").value("How does photosynthesis convert light energy?"))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].role").value("USER"))
                .andExpect(jsonPath("$.messages[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.messages[1].citations[0].chunkId").value(chunkId));
        mvc.perform(get("/api/workspaces/{ws}/conversations", ws))
                .andExpect(jsonPath("$.items[0].id").value(conv))
                .andExpect(jsonPath("$.items[0].messageCount").value(2));

        // Follow-up questions see earlier questions as context, never as sources.
        ask(ws, conv, "What do stars fuse in their cores?").andExpect(jsonPath("$.answer.status").value("COMPLETE"));
        assertThat(chatModel.lastUserPrompt()).contains("<history>").contains("How does photosynthesis convert light energy?");
        assertThat(chatModel.lastSystemPrompt()).contains("untrusted data");
    }

    @Test
    void pdfCitationsCarryPageNumbers() throws Exception {
        String ws = createWorkspace("Pdf chat");
        String id = JsonPath.read(mvc.perform(multipart("/api/workspaces/{ws}/documents", ws)
                        .file(new MockMultipartFile("file", "astro.pdf", "application/pdf",
                                TestFiles.pdf("Introduction to the night sky.", "Stars fuse hydrogen into helium in their cores."))))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString(), "$.id");
        awaitIndex(ws, id, "INDEXED");

        ask(ws, createConversation(ws), "What do stars fuse hydrogen into?")
                .andExpect(jsonPath("$.answer.status").value("COMPLETE"))
                .andExpect(jsonPath("$.answer.citations[0].documentId").value(id))
                .andExpect(jsonPath("$.answer.citations[0].pageNumber").value(2))
                .andExpect(jsonPath("$.answer.citations[0].documentTitle").value("astro"));
    }

    @Test
    void citationsStayAuditableAfterTheirDocumentIsDeleted() throws Exception {
        String ws = createWorkspace("Audit");
        String doc = indexed(ws, "photosynthesis.txt", PHOTOSYNTHESIS);
        String conv = createConversation(ws);
        ask(ws, conv, "How does photosynthesis convert light energy?").andExpect(jsonPath("$.answer.status").value("COMPLETE"));

        mvc.perform(delete("/api/workspaces/{ws}/documents/{d}", ws, doc)).andExpect(status().isNoContent());

        mvc.perform(get("/api/workspaces/{ws}/conversations/{c}", ws, conv))
                .andExpect(jsonPath("$.messages[1].citations[0].documentId").value(doc))
                .andExpect(jsonPath("$.messages[1].citations[0].passage").value(PHOTOSYNTHESIS));
    }

    // --- workspace isolation ---

    @Test
    void conversationsAndEvidenceNeverCrossWorkspaces() throws Exception {
        String wsA = createWorkspace("A");
        String wsB = createWorkspace("B");
        String notesA = indexed(wsA, "notes.txt", "The confidential plan is to grow the analytics dashboard. No merger is planned.");
        indexed(wsB, "secret.txt", "Confidential merger plan: acquire Initech in March for twelve million.");
        String convA = createConversation(wsA);

        String body = ask(wsA, convA, "What is the confidential merger plan with Initech?")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        // The model only ever saw workspace A's evidence, and every citation points into A.
        assertThat(chatModel.lastUserPrompt()).contains("analytics dashboard").doesNotContain("Initech in March");
        assertThat(JsonPath.<List<String>>read(body, "$.answer.citations[*].documentId")).containsOnly(notesA);
        assertThat(body).doesNotContain("twelve million");

        mvc.perform(get("/api/workspaces/{ws}/conversations/{c}", wsB, convA)).andExpect(status().isNotFound());
        ask(wsB, convA, "anything").andExpect(status().isNotFound());
        mvc.perform(put("/api/workspaces/{ws}/conversations/{c}", wsB, convA)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"hijack\"}")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/workspaces/{ws}/conversations/{c}", wsB, convA)).andExpect(status().isNotFound());
        mvc.perform(get("/api/workspaces/{ws}/conversations", wsB)).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/workspaces/{ws}/conversations/{c}", wsA, convA)).andExpect(status().isOk());
    }

    // --- abstention and verification ---

    @Test
    void abstainsWithoutCallingTheModelWhenThereIsNoEvidence() throws Exception {
        String ws = createWorkspace("Empty");
        int callsBefore = chatModel.callCount();
        ask(ws, createConversation(ws), "What is the capital of Peru?")
                .andExpect(jsonPath("$.answer.status").value("ABSTAINED"))
                .andExpect(jsonPath("$.answer.content").value(RagAnswer.ABSTENTION_TEXT))
                .andExpect(jsonPath("$.answer.statusDetail").value(containsString("relevant enough")))
                .andExpect(jsonPath("$.answer.retrievedCount").value(0));
        assertThat(chatModel.callCount()).isEqualTo(callsBefore);
    }

    @Test
    void abstainsWhenTheModelSaysTheSourcesAreInsufficient() throws Exception {
        String ws = createWorkspace("Insufficient");
        indexed(ws, "stars.txt", STARS);
        chatModel.setResponder(prompt -> "{\"answerable\": false, \"answer\": \"Lima, probably.\", \"citations\": []}");

        ask(ws, createConversation(ws), "What do stars contain?")
                .andExpect(jsonPath("$.answer.status").value("ABSTAINED"))
                .andExpect(jsonPath("$.answer.content").value(RagAnswer.ABSTENTION_TEXT))
                .andExpect(jsonPath("$.answer.statusDetail").value(containsString("don't contain the answer")));
    }

    @Test
    void fabricatedCitationsAreNeverShown() throws Exception {
        String ws = createWorkspace("Fabrication");
        indexed(ws, "stars.txt", STARS);
        chatModel.setResponder(prompt -> """
                {"answerable": true, "answer": "Stars are made of cheese [1] according to NASA [9].",
                 "citations": [{"source": 1, "quote": "Stars are made of green cheese"}, {"source": 9, "quote": "NASA confirmed the cheese hypothesis"}]}
                """);

        String body = ask(ws, createConversation(ws), "What are stars made of?")
                .andExpect(jsonPath("$.answer.status").value("ABSTAINED"))
                .andExpect(jsonPath("$.answer.statusDetail").value(containsString("could not be verified")))
                .andExpect(jsonPath("$.answer.citations.length()").value(0))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("cheese");
    }

    @Test
    void partiallyVerifiedAnswersKeepOnlyVerifiedCitations() throws Exception {
        String ws = createWorkspace("Partial");
        String stars = indexed(ws, "stars.txt", STARS);
        chatModel.setResponder(prompt -> """
                {"answerable": true, "answer": "Stars fuse hydrogen into helium [1]. They are 4% cheese [2].",
                 "citations": [{"source": 1, "quote": "Stars fuse hydrogen into helium in their cores"}, {"source": 2, "quote": "stars are four percent cheese"}]}
                """);

        ask(ws, createConversation(ws), "What do stars fuse?")
                .andExpect(jsonPath("$.answer.status").value("COMPLETE"))
                .andExpect(jsonPath("$.answer.content").value("Stars fuse hydrogen into helium [1]. They are 4% cheese."))
                .andExpect(jsonPath("$.answer.citations.length()").value(1))
                .andExpect(jsonPath("$.answer.citations[0].documentId").value(stars));
    }

    // --- failures and retry ---

    @Test
    void modelOutageFailsTheTurnAndRetryRecovers() throws Exception {
        String ws = createWorkspace("Outage");
        indexed(ws, "photosynthesis.txt", PHOTOSYNTHESIS);
        String conv = createConversation(ws);

        chatModel.setMode(TestChatModel.Mode.DOWN);
        String body = ask(ws, conv, "How does photosynthesis convert light energy?")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.answer.status").value("FAILED"))
                .andExpect(jsonPath("$.answer.content").value(""))
                .andExpect(jsonPath("$.answer.statusDetail").value(containsString("ollama pull qwen2.5:7b")))
                .andReturn().getResponse().getContentAsString();
        String answerId = JsonPath.read(body, "$.answer.id");

        chatModel.setMode(TestChatModel.Mode.NORMAL);
        mvc.perform(post("/api/workspaces/{ws}/conversations/{c}/messages/{m}/retry", ws, conv, answerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer.id").value(answerId))
                .andExpect(jsonPath("$.answer.status").value("COMPLETE"))
                .andExpect(jsonPath("$.answer.statusDetail").isEmpty())
                .andExpect(jsonPath("$.answer.citations.length()").value(1));
        mvc.perform(get("/api/workspaces/{ws}/conversations/{c}", ws, conv))
                .andExpect(jsonPath("$.messages.length()").value(2));

        // A completed answer cannot be "retried".
        mvc.perform(post("/api/workspaces/{ws}/conversations/{c}/messages/{m}/retry", ws, conv, answerId))
                .andExpect(status().isConflict());
    }

    @Test
    void timeoutsAndMalformedOutputFailGracefully() throws Exception {
        String ws = createWorkspace("Timeouts");
        indexed(ws, "photosynthesis.txt", PHOTOSYNTHESIS);
        String conv = createConversation(ws);

        chatModel.setMode(TestChatModel.Mode.TIMEOUT);
        ask(ws, conv, "How does photosynthesis convert light energy?")
                .andExpect(jsonPath("$.answer.status").value("FAILED"))
                .andExpect(jsonPath("$.answer.statusDetail").value(containsString("took too long")));

        chatModel.setMode(TestChatModel.Mode.NORMAL);
        chatModel.setResponder(prompt -> "Sure! Photosynthesis is great.");
        ask(ws, conv, "How does photosynthesis convert light energy?")
                .andExpect(jsonPath("$.answer.status").value("FAILED"))
                .andExpect(jsonPath("$.answer.statusDetail").value(containsString("unexpected format")));
    }

    @Test
    void embeddingOutageFailsWithoutCallingTheModel() throws Exception {
        String ws = createWorkspace("No embeddings");
        indexed(ws, "photosynthesis.txt", PHOTOSYNTHESIS);
        String conv = createConversation(ws);
        int callsBefore = chatModel.callCount();
        embeddingModel.setAvailable(false);

        ask(ws, conv, "How does photosynthesis work?")
                .andExpect(jsonPath("$.answer.status").value("FAILED"))
                .andExpect(jsonPath("$.answer.statusDetail").value(containsString("embedding service")));
        assertThat(chatModel.callCount()).isEqualTo(callsBefore);
    }

    @Test
    void onlyOneAnswerIsGeneratedAtATimePerConversation() throws Exception {
        String ws = createWorkspace("Serial");
        indexed(ws, "photosynthesis.txt", PHOTOSYNTHESIS);
        String conv = createConversation(ws);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        chatModel.setResponder(prompt -> {
            entered.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return TestChatModelAccess.grounded(prompt);
        });

        CompletableFuture<String> first = CompletableFuture.supplyAsync(() -> {
            try {
                return ask(ws, conv, "How does photosynthesis convert light energy?").andReturn().getResponse().getContentAsString();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        ask(ws, conv, "A second question while the first is pending")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("still being answered")));
        release.countDown();
        assertThat((String) JsonPath.read(first.get(10, TimeUnit.SECONDS), "$.answer.status")).isEqualTo("COMPLETE");
    }

    @Test
    void answersInterruptedByARestartBecomeRetryable() throws Exception {
        String ws = createWorkspace("Restart");
        indexed(ws, "photosynthesis.txt", PHOTOSYNTHESIS);
        String conv = createConversation(ws);
        String answerId = JsonPath.read(ask(ws, conv, "How does photosynthesis convert light energy?")
                .andReturn().getResponse().getContentAsString(), "$.answer.id");
        jdbc.update("UPDATE messages SET status = 'PENDING', content = '' WHERE id = ?::uuid", answerId);
        jdbc.update("DELETE FROM citations WHERE message_id = ?::uuid", answerId);

        chatService.failInterruptedAnswers();

        mvc.perform(get("/api/workspaces/{ws}/conversations/{c}", ws, conv))
                .andExpect(jsonPath("$.messages[1].status").value("FAILED"))
                .andExpect(jsonPath("$.messages[1].statusDetail").value(ChatService.INTERRUPTED));
        mvc.perform(post("/api/workspaces/{ws}/conversations/{c}/messages/{m}/retry", ws, conv, answerId))
                .andExpect(jsonPath("$.answer.status").value("COMPLETE"));
    }

    // --- validation and lifecycle ---

    @Test
    void invalidRequestsAreRejected() throws Exception {
        String ws = createWorkspace("Validation");
        String conv = createConversation(ws);
        ask(ws, conv, "   ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("content"));
        ask(ws, conv, "x".repeat(4001)).andExpect(status().isBadRequest());
        ask(ws, UUID.randomUUID().toString(), "hello").andExpect(status().isNotFound());
        ask(UUID.randomUUID().toString(), conv, "hello").andExpect(status().isNotFound());
        mvc.perform(put("/api/workspaces/{ws}/conversations/{c}", ws, conv)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\" \"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/workspaces/{ws}/conversations", ws)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"%s\"}".formatted("t".repeat(201))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/workspaces/{ws}/conversations/{c}/messages/{m}/retry", ws, conv, UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void renameAndDeleteCascadeToMessagesAndCitations() throws Exception {
        String ws = createWorkspace("Lifecycle");
        indexed(ws, "photosynthesis.txt", PHOTOSYNTHESIS);
        String conv = createConversation(ws);
        ask(ws, conv, "How does photosynthesis convert light energy?");

        mvc.perform(put("/api/workspaces/{ws}/conversations/{c}", ws, conv)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Plants\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Plants"));
        assertThat(rows("citations", ws)).isEqualTo(1);

        mvc.perform(delete("/api/workspaces/{ws}/conversations/{c}", ws, conv)).andExpect(status().isNoContent());
        assertThat(rows("messages", ws)).isZero();
        assertThat(rows("citations", ws)).isZero();
        mvc.perform(get("/api/workspaces/{ws}/conversations/{c}", ws, conv)).andExpect(status().isNotFound());
    }

    @Test
    void deletingAWorkspaceDeletesItsConversations() throws Exception {
        String ws = createWorkspace("Chats only");
        String conv = createConversation(ws);
        ask(ws, conv, "Anything here?");
        mvc.perform(delete("/api/workspaces/{ws}", ws)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM conversations WHERE id = ?::uuid", Integer.class, conv)).isZero();
    }

    // --- helpers ---

    private String indexed(String ws, String filename, String text) throws Exception {
        String id = uploadText(ws, filename, text);
        awaitIndex(ws, id, "INDEXED");
        return id;
    }

    private String createConversation(String ws) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/workspaces/{ws}/conversations", ws))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions ask(String ws, String conv, String question) throws Exception {
        return mvc.perform(post("/api/workspaces/{ws}/conversations/{c}/messages", ws, conv)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":" + TestChatModel.quote(question) + "}"));
    }

    private int rows(String table, String ws) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE workspace_id = ?::uuid", Integer.class, ws);
    }

    /** Exposes the stub's default grounded reader to scripted responders. */
    static final class TestChatModelAccess {
        static String grounded(String prompt) {
            TestChatModel reader = new TestChatModel();
            return reader.call(new org.springframework.ai.chat.prompt.Prompt(
                    List.of(new org.springframework.ai.chat.messages.UserMessage(prompt))))
                    .getResult().getOutput().getText();
        }
    }
}
