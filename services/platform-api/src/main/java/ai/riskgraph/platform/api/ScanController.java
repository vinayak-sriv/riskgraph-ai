package ai.riskgraph.platform.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ScanController {
    private final ai.riskgraph.platform.service.SourceScanService scans;
    private final ai.riskgraph.platform.security.GithubConnectionGuard github;
    private final ai.riskgraph.platform.security.ScanAccessService access;
    public ScanController(ai.riskgraph.platform.service.SourceScanService scans,
        ai.riskgraph.platform.security.GithubConnectionGuard github,
        ai.riskgraph.platform.security.ScanAccessService access) {
        this.scans = scans; this.github = github; this.access = access;
    }
    @PostMapping("/scans")
    public CreateScanResponse createScan(@Valid @RequestBody CreateScanRequest request) {
        github.requireLinked();
        var result = scans.analyze(request.repository(), request.old_commit(), request.new_commit());
        access.claim(result.path("scan_id").asString());
        return new CreateScanResponse(result.path("scan_id").asString(), result.path("status").asString());
    }

    @GetMapping("/scans/{scanId}")
    public ScanResult getScan(@PathVariable String scanId) {
        github.requireLinked();
        access.requireView(scanId);
        var result = scans.get(scanId);
        var findings = new java.util.ArrayList<FindingSummary>();
        for (var finding : result.path("findings")) findings.add(new FindingSummary(
                finding.path("finding_id").asString(), "Anonymous sensitive path",
                finding.path("severity").asString(), finding.at("/evidence/0").asString()));
        return new ScanResult(scanId, result.path("status").asString(), result.path("final_verdict").asString(),
            result.at("/risk_result/risk_before").asInt(), result.at("/risk_result/risk_after").asInt(),
            result.at("/risk_result/risk_delta").asInt(), List.copyOf(findings));
    }

    public record CreateScanRequest(
            @NotBlank String repository,
            @NotBlank String old_commit,
            @NotBlank String new_commit
    ) {
    }

    public record CreateScanResponse(
            String scan_id,
            String status
    ) {
    }

    public record ScanResult(
            String scan_id,
            String status,
            String decision,
            @Min(0) @Max(100) int risk_before,
            @Min(0) @Max(100) int risk_after,
            @Min(-100) @Max(100) int risk_delta,
            List<FindingSummary> findings
    ) {
    }

    public record FindingSummary(
            String finding_id,
            String type,
            String severity,
            String description
    ) {
    }
}
