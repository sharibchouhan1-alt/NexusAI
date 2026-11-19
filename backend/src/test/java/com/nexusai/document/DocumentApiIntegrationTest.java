package com.nexusai.document;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexusai.ApiTestSupport;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class DocumentApiIntegrationTest extends ApiTestSupport {

    // --- success paths ---

    @Test
    void registerReturns201WithMetadata() throws Exception {
        String ws = createWorkspace("Docs");

        mvc.perform(post("/api/workspaces/{ws}/documents", ws)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":" Attention Is All You Need ","sourceType":"URL",
                                 "origin":"https://arxiv.org/abs/1706.03762"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        matchesPattern("^/api/workspaces/" + ws + "/documents/[0-9a-f-]{36}$")))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.workspaceId").value(ws))
                .andExpect(jsonPath("$.title").value("Attention Is All You Need"))
                .andExpect(jsonPath("$.sourceType").value("URL"))
                .andExpect(jsonPath("$.mediaType").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.origin").value("https://arxiv.org/abs/1706.03762"))
                .andExpect(jsonPath("$.status").value("REGISTERED"));
    }

    @Test
    void getListUpdateDelete() throws Exception {
        String ws = createWorkspace("Docs");
        String first = createDocument(ws, "First");
        String second = createDocument(ws, "Second");

        mvc.perform(get("/api/workspaces/{ws}/documents/{id}", ws, first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("First"))
                .andExpect(jsonPath("$.mediaType").value("application/pdf"));

        mvc.perform(get("/api/workspaces/{ws}/documents", ws))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].id").value(second)) // newest first
                .andExpect(jsonPath("$.items[1].id").value(first));

        mvc.perform(get("/api/workspaces/{ws}/documents", ws).param("size", "1").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(first))
                .andExpect(jsonPath("$.totalPages").value(2));

        mvc.perform(put("/api/workspaces/{ws}/documents/{id}", ws, first)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed"))
                .andExpect(jsonPath("$.sourceType").value("FILE"));

        mvc.perform(delete("/api/workspaces/{ws}/documents/{id}", ws, first))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/workspaces/{ws}/documents/{id}", ws, first))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/workspaces/{ws}/documents", ws))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    // --- invalid input ---

    @Test
    void missingRequiredFieldsAreRejected() throws Exception {
        String ws = createWorkspace("Docs");
        mvc.perform(post("/api/workspaces/{ws}/documents", ws)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[*].field",
                        Matchers.containsInAnyOrder("title", "sourceType")));
    }

    @Test
    void unknownSourceTypeIsRejected() throws Exception {
        String ws = createWorkspace("Docs");
        mvc.perform(post("/api/workspaces/{ws}/documents", ws)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"sourceType\":\"FAX\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void invalidMediaTypeIsRejected() throws Exception {
        String ws = createWorkspace("Docs");
        mvc.perform(post("/api/workspaces/{ws}/documents", ws)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"sourceType\":\"FILE\",\"mediaType\":\"pdf\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("mediaType"));
    }

    @Test
    void blankTitleOnUpdateIsRejected() throws Exception {
        String ws = createWorkspace("Docs");
        String doc = createDocument(ws, "Doc");
        mvc.perform(put("/api/workspaces/{ws}/documents/{id}", ws, doc)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
    }

    // --- missing resources ---

    @Test
    void missingWorkspaceReturns404() throws Exception {
        UUID missing = UUID.randomUUID();
        mvc.perform(post("/api/workspaces/{ws}/documents", missing)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"sourceType\":\"FILE\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/workspaces/{ws}/documents", missing))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingDocumentReturns404() throws Exception {
        String ws = createWorkspace("Docs");
        UUID missing = UUID.randomUUID();
        mvc.perform(get("/api/workspaces/{ws}/documents/{id}", ws, missing))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        mvc.perform(put("/api/workspaces/{ws}/documents/{id}", ws, missing)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/workspaces/{ws}/documents/{id}", ws, missing))
                .andExpect(status().isNotFound());
    }

    // --- workspace isolation ---

    @Test
    void documentsAreInvisibleOutsideTheirWorkspace() throws Exception {
        String wsA = createWorkspace("A");
        String wsB = createWorkspace("B");
        String docA = createDocument(wsA, "Secret in A");

        // B's listing does not include A's document.
        mvc.perform(get("/api/workspaces/{ws}/documents", wsB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));

        // Addressing A's document through B is indistinguishable from a missing document.
        String missingBody = mvc.perform(get("/api/workspaces/{ws}/documents/{id}", wsB, UUID.randomUUID()))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/workspaces/{ws}/documents/{id}", wsB, docA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value(
                        com.jayway.jsonpath.JsonPath.<String>read(missingBody, "$.title")));
        mvc.perform(put("/api/workspaces/{ws}/documents/{id}", wsB, docA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"hijacked\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/workspaces/{ws}/documents/{id}", wsB, docA))
                .andExpect(status().isNotFound());

        // A's document is untouched.
        mvc.perform(get("/api/workspaces/{ws}/documents/{id}", wsA, docA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Secret in A"));
    }
}
