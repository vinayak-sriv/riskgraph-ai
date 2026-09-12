package ai.riskgraph.analyzer.service;

import ai.riskgraph.analyzer.model.AnalysisModels.ExtractionCoverage;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CoverageAggregationTest {
    @Test void failedEmptySnapshotCannotBecomePerfectCoverage() {
        var failed = new ExtractionCoverage(5, 0, 0, 0, 0, 0.0);
        var complete = new ExtractionCoverage(1, 1, 1, 1, 1, 1.0);
        assertThat(AnalysisService.combine(failed, failed).coverage_ratio()).isZero();
        assertThat(AnalysisService.combine(failed, complete).coverage_ratio()).isZero();
        assertThat(AnalysisService.combine(complete, failed).coverage_ratio()).isZero();
    }

    @Test void successfulEmptySurfaceDoesNotPenalizeNewEndpoints() {
        var empty = new ExtractionCoverage(1, 0, 0, 0, 0, 1.0);
        var complete = new ExtractionCoverage(1, 1, 1, 1, 1, 1.0);
        assertThat(AnalysisService.combine(empty, empty).coverage_ratio()).isEqualTo(1.0);
        assertThat(AnalysisService.combine(empty, complete).coverage_ratio()).isEqualTo(1.0);
    }
}
