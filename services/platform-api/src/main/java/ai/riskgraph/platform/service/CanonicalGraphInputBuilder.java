package ai.riskgraph.platform.service;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class CanonicalGraphInputBuilder {
    static final int MAX_ROWS_PER_REVISION = 2000;
    private final ObjectMapper mapper;

    public CanonicalGraphInputBuilder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public BuildResult build(JsonNode envelope) {
        ObjectNode graphInput = mapper.createObjectNode();
        String confidence = "HIGH";
        boolean incomplete = false;
        for (String revision : new String[]{"before", "after"}) {
            ArrayNode rows = graphInput.putArray(revision);
            for (JsonNode evidence : envelope.path(revision)) {
                String level = evidence.at("/extraction_confidence/overall").asString();
                confidence = lowerConfidence(confidence, level);
                incomplete |= !level.equals("HIGH");
                addCanonicalPaths(rows, evidence);
            }
        }
        for (JsonNode diagnostic : envelope.path("diagnostics")) {
            incomplete |= !diagnostic.path("severity").asString().equals("INFO");
            if (diagnostic.path("severity").asString().equals("ERROR")) confidence = "LOW";
        }
        boolean emptyChangedSurface = !envelope.path("changed_files").isEmpty()
                && envelope.path("before").isEmpty() && envelope.path("after").isEmpty();
        incomplete |= emptyChangedSurface;
        if (emptyChangedSurface) confidence = "LOW";
        graphInput.putObject("quality").put("confidence", confidence)
                .put("incomplete", incomplete)
                .put("coverage_ratio", envelope.at("/coverage/coverage_ratio").asDouble());
        return new BuildResult(graphInput, confidence, incomplete);
    }

    private String lowerConfidence(String current, String candidate) {
        if (candidate.equals("LOW")) return "LOW";
        if (candidate.equals("MEDIUM") && current.equals("HIGH")) return "MEDIUM";
        return current;
    }

    private void addCanonicalPaths(ArrayNode rows, JsonNode evidence) {
        JsonNode endpoint = evidence.path("endpoint");
        JsonNode paths = evidence.path("dependency_paths");
        if (paths.isEmpty()) {
            addCanonicalRow(rows, endpoint);
            return;
        }
        for (JsonNode path : paths) {
            ObjectNode canonical = (ObjectNode) endpoint.deepCopy();
            copyNullable(canonical, "service", path.path("service"));
            copyNullable(canonical, "repository", path.path("repository"));
            canonical.put("resource", path.path("resource").asString());
            canonical.put("sensitivity", path.path("sensitivity").asString());
            addCanonicalRow(rows, canonical);
        }
    }

    private void addCanonicalRow(ArrayNode rows, JsonNode row) {
        if (rows.size() >= MAX_ROWS_PER_REVISION) {
            throw new PipelineException("ANALYSIS_TOO_LARGE", 413,
                    "Canonical graph input exceeds 2000 rows per revision");
        }
        rows.add(row);
    }

    private void copyNullable(ObjectNode target, String name, JsonNode value) {
        if (value.isNull() || value.isMissingNode()) target.putNull(name);
        else target.put(name, value.asString());
    }

    public record BuildResult(ObjectNode graphInput, String confidence, boolean incomplete) { }
}
