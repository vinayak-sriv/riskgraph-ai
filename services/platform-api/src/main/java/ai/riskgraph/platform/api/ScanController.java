package ai.riskgraph.platform.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ScanController {
    @PostMapping("/scans")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CreateScanResponse createScan(@Valid @RequestBody CreateScanRequest request) {
        return new CreateScanResponse(UUID.randomUUID().toString(), "PENDING");
    }

    @GetMapping("/scans/{scanId}")
    public ScanResult getScan(@PathVariable String scanId) {
        return new ScanResult(scanId, "PENDING", null, 0, 0, 0, List.of());
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
            String type,
            String severity,
            String description
    ) {
    }
}
