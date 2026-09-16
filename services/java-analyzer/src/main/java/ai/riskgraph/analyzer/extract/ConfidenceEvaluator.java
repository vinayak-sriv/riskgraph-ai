package ai.riskgraph.analyzer.extract;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

final class ConfidenceEvaluator {
    String callConfidence(DependencyPathResolver.Result resolution) {
        if (resolution.ambiguous()) return "LOW";
        if (resolution.paths().stream().anyMatch(path -> path.repository() != null)) return "HIGH";
        return resolution.paths().isEmpty() ? "LOW" : "MEDIUM";
    }

    String minimum(String... values) {
        List<String> order = List.of("LOW", "MEDIUM", "HIGH");
        return Arrays.stream(values).min(Comparator.comparingInt(order::indexOf)).orElse("LOW");
    }
}
