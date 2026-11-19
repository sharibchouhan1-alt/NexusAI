package com.nexusai.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.nexusai.ApiTestSupport;
import com.nexusai.TestcontainersConfiguration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * The size limit is enforced by the servlet container's multipart parsing, which MockMvc
 * bypasses, so this test goes through a real HTTP server with a tiny limit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        ApiTestSupport.TEST_STORAGE_ROOT,
        "spring.servlet.multipart.max-file-size=1KB",
        "spring.servlet.multipart.max-request-size=2KB",
})
class UploadSizeLimitIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    void oversizedUploadIsRejectedWith413() {
        RestClient client = RestClient.create("http://localhost:" + port);
        @SuppressWarnings("unchecked")
        Map<String, Object> workspace = client.post().uri("/api/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Limits"))
                .retrieve().body(Map.class);

        var form = new LinkedMultiValueMap<String, Object>();
        form.add("file", new ByteArrayResource("a".repeat(1500).getBytes()) {
            @Override
            public String getFilename() {
                return "big.txt";
            }
        });

        HttpClientErrorException error = catchThrowableOfType(HttpClientErrorException.class, () ->
                client.post().uri("/api/workspaces/{ws}/documents", workspace.get("id"))
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(form)
                        .retrieve().toBodilessEntity());

        assertThat(error).isNotNull();
        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(error.getResponseHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(error.getResponseBodyAsString()).contains("maximum upload size");
    }
}
