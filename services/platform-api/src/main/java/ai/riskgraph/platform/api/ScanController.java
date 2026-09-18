package ai.riskgraph.platform.api;

import ai.riskgraph.platform.security.GithubConnectionGuard;
import ai.riskgraph.platform.security.ScanAccessService;
import ai.riskgraph.platform.service.AsyncScanJobService;
import ai.riskgraph.platform.service.ScanJobStore;
import ai.riskgraph.platform.service.SourceScanService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ScanController {
    private final SourceScanService scans;
    private final AsyncScanJobService jobs;
    private final GithubConnectionGuard github;
    private final ScanAccessService access;

    public ScanController(SourceScanService scans, AsyncScanJobService jobs,
            GithubConnectionGuard github, ScanAccessService access) {
        this.scans = scans;
        this.jobs = jobs;
        this.github = github;
        this.access = access;
    }

    @PostMapping("/scans")
    public ResponseEntity<CreateScanResponse> createScan(
            @Valid @RequestBody CreateScanRequest request, Authentication authentication) {
        github.requireLinked();
        AsyncScanJobService.Submission submission = jobs.submit(authentication.getName(),
                request.repository(), request.old_commit(), request.new_commit());
        return ResponseEntity.accepted().body(new CreateScanResponse(
                submission.job().jobId(), submission.job().state().name(),
                submission.job().scanId(), submission.reused()));
    }

    @GetMapping("/scan-jobs/{jobId}")
    public JobResponse getJob(@PathVariable UUID jobId, Authentication authentication) {
        github.requireLinked();
        return response(jobs.requireVisible(jobId, authentication.getName(), isAdmin(authentication)));
    }

    @GetMapping("/scan-jobs/{jobId}/result")
    public tools.jackson.databind.JsonNode getJobResult(
            @PathVariable UUID jobId, Authentication authentication) {
        github.requireLinked();
        return jobs.result(jobId, authentication.getName(), isAdmin(authentication));
    }

    @DeleteMapping("/scan-jobs/{jobId}")
    public JobResponse cancelJob(@PathVariable UUID jobId, Authentication authentication) {
        github.requireLinked();
        return response(jobs.cancel(jobId, authentication.getName(), isAdmin(authentication)));
    }

    @GetMapping("/scans")
    public ScanHistory scanHistory(@RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            Authentication authentication) {
        github.requireLinked();
        AsyncScanJobService.JobPage page = jobs.list(authentication.getName(),
                isAdmin(authentication), cursor, limit);
        List<HistoryItem> items = new ArrayList<>();
        // Only fetch summaries for the page already filtered by job ownership/membership.
        var summaries = scans.summaries(page.jobs().stream().map(ScanJobStore.ScanJob::scanId)
                .filter(java.util.Objects::nonNull).distinct().toList());
        for (ScanJobStore.ScanJob job : page.jobs()) {
            items.add(historyItem(job, job.scanId() == null ? null : summaries.get(job.scanId())));
        }
        return new ScanHistory(List.copyOf(items), page.nextCursor());
    }

    @GetMapping("/scans/{scanId}")
    public ScanResult getScan(@PathVariable String scanId) {
        github.requireLinked();
        access.requireView(scanId);
        var result = scans.get(scanId);
        var findings = new ArrayList<FindingSummary>();
        for (var finding : result.path("findings")) findings.add(new FindingSummary(
                finding.path("finding_id").asString(), "Anonymous sensitive path",
                finding.path("severity").asString(), finding.at("/evidence/0").asString()));
        return new ScanResult(scanId, result.path("status").asString(),
                result.path("final_verdict").asString(),
                result.at("/risk_result/risk_before").asInt(),
                result.at("/risk_result/risk_after").asInt(),
                result.at("/risk_result/risk_delta").asInt(), List.copyOf(findings));
    }

    private HistoryItem historyItem(ScanJobStore.ScanJob job,
            ai.riskgraph.platform.service.ScanStore.Summary summary) {
        Integer riskDelta = summary == null ? null : summary.riskDelta();
        String verdict = summary == null ? null : summary.verdict();
        return new HistoryItem(job.jobId(), job.scanId(), job.repository(), job.oldCommit(),
                job.newCommit(), job.state().name(), riskDelta, verdict, job.updatedAt());
    }

    private JobResponse response(ScanJobStore.ScanJob job) {
        return new JobResponse(job.jobId(), job.state().name(), job.stage(), job.progress(),
                job.reasonCode(), job.scanId(), job.createdAt(), job.updatedAt());
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }

    public record CreateScanRequest(@NotBlank String repository,
            @NotBlank @Pattern(regexp = "[0-9a-fA-F]{40}") String old_commit,
            @NotBlank @Pattern(regexp = "[0-9a-fA-F]{40}") String new_commit) { }
    public record CreateScanResponse(UUID job_id, String status, String scan_id,
            boolean reused) { }
    public record JobResponse(UUID job_id, String status, String stage, int progress,
            String reason_code, String scan_id, Instant created_at, Instant updated_at) { }
    public record ScanHistory(List<HistoryItem> items, String next_cursor) { }
    public record HistoryItem(UUID job_id, String scan_id, String repository,
            String old_commit, String new_commit, String status, Integer risk_delta,
            String verdict, Instant updated_at) { }
    public record ScanResult(String scan_id, String status, String decision,
            @Min(0) @Max(100) int risk_before, @Min(0) @Max(100) int risk_after,
            @Min(-100) @Max(100) int risk_delta, List<FindingSummary> findings) { }
    public record FindingSummary(String finding_id, String type, String severity,
            String description) { }
}
