package ai.riskgraph.analyzer.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import ai.riskgraph.analyzer.model.AnalysisModels.Diagnostic;
import ai.riskgraph.analyzer.model.AnalysisModels.EndpointEvidence;
import ai.riskgraph.analyzer.model.AnalysisModels.EndpointIr;
import ai.riskgraph.analyzer.model.AnalysisModels.ExtractionConfidence;
import ai.riskgraph.analyzer.model.AnalysisModels.ExtractionCoverage;
import ai.riskgraph.analyzer.model.AnalysisModels.SensitivityEvidence;
import ai.riskgraph.analyzer.model.AnalysisModels.SourceLocation;
import ai.riskgraph.analyzer.service.DeterministicExtractionCache.CachedExtraction;

class DeterministicExtractionCacheTest {
    @Test
    void copiesMutableListsAndEvictsLeastRecentlyUsedEntry() {
        DeterministicExtractionCache cache = new DeterministicExtractionCache(2);
        List<EndpointEvidence> before = new ArrayList<>(List.of(evidence("/first")));
        List<Diagnostic> diagnostics = new ArrayList<>();
        CachedExtraction first = new CachedExtraction(before, List.of(), coverage(), diagnostics);

        assertThat(cache.get("first")).isEmpty();
        cache.put("first", first);
        before.clear();
        diagnostics.add(new Diagnostic("ERROR", "MUTATED", "must not leak", null));
        cache.put("second", new CachedExtraction(List.of(evidence("/second")), List.of(), coverage(), List.of()));
        assertThat(cache.get("first")).isPresent();
        assertThat(cache.put("third",
                new CachedExtraction(List.of(evidence("/third")), List.of(), coverage(), List.of()))).isTrue();

        assertThat(cache.get("first")).isPresent();
        assertThat(cache.get("second")).isEmpty();
        assertThat(cache.get("first").orElseThrow().before()).singleElement()
                .extracting(item -> item.endpoint().endpoint()).isEqualTo("/first");
        assertThat(cache.get("first").orElseThrow().diagnostics()).isEmpty();
        assertThat(cache.stats().evictions()).isEqualTo(1);
        assertThat(cache.stats().entries()).isEqualTo(2);
    }

    private EndpointEvidence evidence(String path) {
        return new EndpointEvidence(
                new EndpointIr(path, "GET", "Controller", false, null,
                        null, null, null, "LOW"),
                new SourceLocation("Controller.java", 1, 1),
                "demo.Controller", "get()", List.of(),
                new SensitivityEvidence("LOW", "default", null),
                new ExtractionConfidence("HIGH", "HIGH", "HIGH", "HIGH"));
    }

    private ExtractionCoverage coverage() {
        return new ExtractionCoverage(1, 1, 1, 0, 0, 0.0);
    }
}
