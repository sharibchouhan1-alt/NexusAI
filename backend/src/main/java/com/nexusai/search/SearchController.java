package com.nexusai.search;

import com.nexusai.platform.web.InvalidRequestException;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/search")
@EnableConfigurationProperties(SearchProperties.class)
class SearchController {

    private final SearchService service;
    private final SearchProperties properties;

    SearchController(SearchService service, SearchProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    /**
     * Semantic search over the workspace's indexed chunks. 503 (retryable) if the embedding
     * service is unavailable; 404 for an unknown workspace.
     */
    @GetMapping
    SearchResponse search(@PathVariable UUID workspaceId,
                          @RequestParam("q") @NotBlank @Size(max = 1000) String query,
                          @RequestParam(required = false) @Min(1) Integer limit) {
        int effective = limit == null ? properties.defaultLimit() : limit;
        if (effective > properties.maxLimit()) {
            throw new InvalidRequestException("limit must be at most " + properties.maxLimit());
        }
        return service.search(workspaceId, query, effective);
    }
}
