package ai.riskgraph.analyzer.model;

import java.time.Instant;
import java.util.List;

public final class AnalysisModels {
    private AnalysisModels() {
    }

    public record EndpointIr(
            String endpoint,
            String method,
            String controller,
            boolean authentication,
            String required_role,
            String service,
            String repository,
            String resource,
            String sensitivity
    ) {
    }

    public record SourceLocation(String path, int start_line, int end_line) {
    }

    public record DependencyPath(String service, String repository, String resource, String sensitivity) {
    }

    public record SensitivityEvidence(String classification, String source, String matched_rule) {
    }

    public record ExtractionConfidence(
            String route,
            String authorization,
            String call_resolution,
            String overall
    ) {
    }

    public record EndpointEvidence(
            EndpointIr endpoint,
            SourceLocation source_location,
            String qualified_controller,
            String method_signature,
            List<DependencyPath> dependency_paths,
            SensitivityEvidence sensitivity_evidence,
            ExtractionConfidence extraction_confidence
    ) {
    }

    public record ChangedRange(int start_line, int end_line) {
    }

    public record ChangedFile(
            String status,
            String old_path,
            String new_path,
            List<ChangedRange> old_ranges,
            List<ChangedRange> new_ranges
    ) {
    }

    public record RepositoryProvenance(
            String repository_path,
            String repository_identity,
            String old_commit,
            String new_commit
    ) {
    }

    public record ExtractionCoverage(
            int java_files_considered,
            int controllers_discovered,
            int endpoints_emitted,
            int endpoints_with_service,
            int endpoints_with_repository,
            double coverage_ratio
    ) {
    }

    public record Diagnostic(String severity, String code, String message, String path) {
    }

    public record AnalysisResponse(
            String schema_version,
            String analyzer_version,
            String analyzer_config_hash,
            String analysis_id,
            Instant analyzed_at,
            RepositoryProvenance provenance,
            List<ChangedFile> changed_files,
            List<EndpointEvidence> before,
            List<EndpointEvidence> after,
            ExtractionCoverage coverage,
            List<Diagnostic> diagnostics
    ) {
    }
}
