package com.nexusai.workspace;

import static org.hamcrest.Matchers.hasItem;
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
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class WorkspaceApiIntegrationTest extends ApiTestSupport {

    // --- success paths ---

    @Test
    void createReturns201WithLocationAndBody() throws Exception {
        mvc.perform(post("/api/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  Research  \",\"description\":\"Papers\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        matchesPattern("^/api/workspaces/[0-9a-f-]{36}$")))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name").value("Research"))
                .andExpect(jsonPath("$.description").value("Papers"))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.updatedAt", notNullValue()));
    }

    @Test
    void getListUpdateDelete() throws Exception {
        String id = createWorkspace("Alpha");

        mvc.perform(get("/api/workspaces/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Alpha"));

        mvc.perform(get("/api/workspaces").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", hasItem(id)))
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.totalItems", notNullValue()));

        mvc.perform(put("/api/workspaces/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Beta\",\"description\":\"renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Beta"))
                .andExpect(jsonPath("$.description").value("renamed"));

        mvc.perform(delete("/api/workspaces/{id}", id))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/workspaces/{id}", id))
                .andExpect(status().isNotFound());
    }

    // --- invalid input ---

    @Test
    void blankNameIsRejectedWithFieldErrors() throws Exception {
        mvc.perform(post("/api/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void overlongNameIsRejected() throws Exception {
        mvc.perform(post("/api/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\"}".formatted("x".repeat(201))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
        mvc.perform(post("/api/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void invalidIdIsRejected() throws Exception {
        mvc.perform(get("/api/workspaces/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void invalidPagingIsRejected() throws Exception {
        mvc.perform(get("/api/workspaces").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"));
        mvc.perform(get("/api/workspaces").param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    // --- missing resources ---

    @Test
    void missingWorkspaceReturns404() throws Exception {
        UUID missing = UUID.randomUUID();
        mvc.perform(get("/api/workspaces/{id}", missing))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
        mvc.perform(put("/api/workspaces/{id}", missing)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/workspaces/{id}", missing))
                .andExpect(status().isNotFound());
    }

    // --- relationship constraint ---

    @Test
    void deletingNonEmptyWorkspaceReturns409() throws Exception {
        String id = createWorkspace("Has docs");
        String documentId = createDocument(id, "Doc");

        mvc.perform(delete("/api/workspaces/{id}", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        // Nothing was removed.
        mvc.perform(get("/api/workspaces/{id}/documents/{doc}", id, documentId))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/workspaces/{id}/documents/{doc}", id, documentId))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/workspaces/{id}", id))
                .andExpect(status().isNoContent());
    }
}
