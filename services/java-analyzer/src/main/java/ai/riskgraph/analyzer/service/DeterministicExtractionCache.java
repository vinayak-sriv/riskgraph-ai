package ai.riskgraph.analyzer.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import ai.riskgraph.analyzer.model.AnalysisModels.Diagnostic;
import ai.riskgraph.analyzer.model.AnalysisModels.EndpointEvidence;
import ai.riskgraph.analyzer.model.AnalysisModels.ExtractionCoverage;

/**
 * Bounded process-local cache for immutable analyzer output records.
 *
 * <p>Resolved parser/model objects are deliberately excluded. The key is the
 * canonical analysis identity, which already includes repository identity,
 * both full commit SHAs, analyzer version, and configuration fingerprint.</p>
 */
final class DeterministicExtractionCache {
    private final int maxEntries;
    private final Map<String, CachedExtraction> entries;
    private long hits;
    private long misses;
    private long evictions;

    DeterministicExtractionCache(int maxEntries) {
        this.maxEntries = Math.max(1, maxEntries);
        this.entries = new LinkedHashMap<>(16, 0.75f, true);
    }

    synchronized Optional<CachedExtraction> get(String analysisId) {
        CachedExtraction cached = entries.get(analysisId);
        if (cached == null) {
            misses++;
            return Optional.empty();
        }
        hits++;
        return Optional.of(cached);
    }

    synchronized boolean put(String analysisId, CachedExtraction extraction) {
        entries.put(analysisId, extraction.immutableCopy());
        if (entries.size() <= maxEntries) return false;
        String eldest = entries.keySet().iterator().next();
        entries.remove(eldest);
        evictions++;
        return true;
    }

    synchronized CacheStats stats() {
        return new CacheStats(hits, misses, evictions, entries.size());
    }

    record CachedExtraction(
            List<EndpointEvidence> before,
            List<EndpointEvidence> after,
            ExtractionCoverage coverage,
            List<Diagnostic> diagnostics
    ) {
        CachedExtraction immutableCopy() {
            return new CachedExtraction(
                    freezeEvidence(before), freezeEvidence(after), coverage, List.copyOf(diagnostics));
        }

        private static List<EndpointEvidence> freezeEvidence(List<EndpointEvidence> evidence) {
            return evidence.stream().map(item -> new EndpointEvidence(
                    item.endpoint(), item.source_location(), item.qualified_controller(),
                    item.method_signature(), List.copyOf(item.dependency_paths()),
                    item.sensitivity_evidence(), item.extraction_confidence())).toList();
        }
    }

    record CacheStats(long hits, long misses, long evictions, int entries) { }
}
