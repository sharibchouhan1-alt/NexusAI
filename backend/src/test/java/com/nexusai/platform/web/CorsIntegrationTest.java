package com.nexusai.platform.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexusai.ApiTestSupport;
import org.junit.jupiter.api.Test;

class CorsIntegrationTest extends ApiTestSupport {

    @Test
    void preflightFromConfiguredFrontendOriginIsAllowed() throws Exception {
        mvc.perform(options("/api/workspaces")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void simpleRequestExposesLocationHeader() throws Exception {
        mvc.perform(get("/api/workspaces").header("Origin", "http://localhost:3000"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Expose-Headers", "Location"));
    }

    @Test
    void unknownOriginIsRejected() throws Exception {
        mvc.perform(options("/api/workspaces")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "DELETE"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
