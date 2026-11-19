package com.nexusai.research;

import com.nexusai.platform.web.PageResponse;
import com.nexusai.research.ResearchDtos.RunDetail;
import com.nexusai.research.ResearchDtos.RunSummary;
import com.nexusai.research.ResearchDtos.StartResearchRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/research")
class ResearchController {

    private final ResearchService service;

    ResearchController(ResearchService service) {
        this.service = service;
    }

    /** Starts a research run in the background (202). Poll GET for progress. */
    @PostMapping
    ResponseEntity<RunDetail> start(@PathVariable UUID workspaceId, @Valid @RequestBody StartResearchRequest request) {
        RunDetail run = service.start(workspaceId, request.objective());
        return ResponseEntity.accepted().location(URI.create("/api/workspaces/" + workspaceId + "/research/" + run.id())).body(run);
    }

    @GetMapping
    PageResponse<RunSummary> list(@PathVariable UUID workspaceId,
                                  @RequestParam(defaultValue = "0") @Min(0) int page,
                                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(workspaceId, page, size);
    }

    @GetMapping("/{runId}")
    RunDetail get(@PathVariable UUID workspaceId, @PathVariable UUID runId) {
        return service.get(workspaceId, runId);
    }

    /** Queued runs stop at once; running ones at their next checkpoint (202). 409 if not running. */
    @PostMapping("/{runId}/cancel")
    ResponseEntity<RunDetail> cancel(@PathVariable UUID workspaceId, @PathVariable UUID runId) {
        return ResponseEntity.accepted().body(service.cancel(workspaceId, runId));
    }

    /** Continues a FAILED or CANCELLED run from where it stopped (202). */
    @PostMapping("/{runId}/resume")
    ResponseEntity<RunDetail> resume(@PathVariable UUID workspaceId, @PathVariable UUID runId) {
        return ResponseEntity.accepted().body(service.resume(workspaceId, runId));
    }

    /** 409 while the run is active (cancel it first). */
    @DeleteMapping("/{runId}")
    ResponseEntity<Void> delete(@PathVariable UUID workspaceId, @PathVariable UUID runId) {
        service.delete(workspaceId, runId);
        return ResponseEntity.noContent().build();
    }
}
