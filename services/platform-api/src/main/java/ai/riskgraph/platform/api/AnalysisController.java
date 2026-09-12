package ai.riskgraph.platform.api;

import ai.riskgraph.platform.service.SourceScanService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import ai.riskgraph.platform.security.GithubConnectionGuard;
import ai.riskgraph.platform.security.ScanAccessService;

@RestController
public class AnalysisController {
    private final SourceScanService scans;
    private final GithubConnectionGuard github;
    private final ScanAccessService access;
    public AnalysisController(SourceScanService scans, GithubConnectionGuard github, ScanAccessService access) {
        this.scans = scans; this.github = github; this.access = access;
    }
    @PostMapping("/analyses")
    public JsonNode analyze(@Valid @RequestBody Request request) {
        github.requireLinked();
        JsonNode result = scans.analyze(request.repository_path(), request.old_commit(), request.new_commit());
        access.claim(result.path("scan_id").asString());
        return result;
    }
    @GetMapping("/analyses/{id}")
    public JsonNode get(@PathVariable String id) {
        github.requireLinked(); access.requireView(id); return scans.get(id);
    }
    @PostMapping("/analyses/{id}/sandbox-demonstration/{revision}")
    public JsonNode validateSandbox(@PathVariable String id, @PathVariable String revision) {
        github.requireLinked();
        access.requireValidation(id);
        return scans.validateSandbox(id, revision);
    }
    @PostMapping("/analyses/{id}/validation")
    public JsonNode validateSource(@PathVariable String id) {
        github.requireLinked(); access.requireValidation(id); return scans.validateSource(id);
    }
    @PostMapping("/analyses/{id}/access")
    public void grantAccess(@PathVariable String id, @Valid @RequestBody AccessRequest request) {
        github.requireLinked(); access.grant(id, request.username(), request.access());
    }
    public record Request(@NotBlank String repository_path,
        @NotBlank @Pattern(regexp="[0-9a-fA-F]{40}") String old_commit,
        @NotBlank @Pattern(regexp="[0-9a-fA-F]{40}") String new_commit) {}
    public record AccessRequest(
        @NotBlank @Pattern(regexp="[a-z][a-z0-9_.-]{2,63}") String username,
        @jakarta.validation.constraints.NotNull ScanAccessService.Access access) {}
}
