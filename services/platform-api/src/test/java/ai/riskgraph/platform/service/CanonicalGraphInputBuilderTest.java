package ai.riskgraph.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class CanonicalGraphInputBuilderTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final CanonicalGraphInputBuilder builder = new CanonicalGraphInputBuilder(mapper);

    @Test
    void preservesExactBoundaryWithoutBuildingRowTwoThousandAndOne() {
        ObjectNode envelope = envelopeWithRows(2000);
        assertThat(builder.build(envelope).graphInput().path("after")).hasSize(2000);

        assertThatThrownBy(() -> builder.build(envelopeWithRows(2001)))
                .isInstanceOf(PipelineException.class)
                .extracting(error -> ((PipelineException) error).code)
                .isEqualTo("ANALYSIS_TOO_LARGE");
    }

    @Test
    void derivesQualityFromEvidenceDiagnosticsAndCoverage() {
        ObjectNode envelope = envelopeWithRows(1);
        ((ObjectNode) envelope.at("/after/0/extraction_confidence")).put("overall", "MEDIUM");
        envelope.withArray("diagnostics").addObject().put("severity", "WARNING");
        envelope.withObject("coverage").put("coverage_ratio", 0.75);

        CanonicalGraphInputBuilder.BuildResult result = builder.build(envelope);

        assertThat(result.confidence()).isEqualTo("MEDIUM");
        assertThat(result.incomplete()).isTrue();
        assertThat(result.graphInput().at("/quality/coverage_ratio").asDouble()).isEqualTo(0.75);
    }

    private ObjectNode envelopeWithRows(int count) {
        ObjectNode envelope = mapper.createObjectNode();
        envelope.putArray("before");
        var after = envelope.putArray("after");
        ObjectNode evidence = after.addObject();
        evidence.putObject("extraction_confidence").put("overall", "HIGH");
        evidence.putObject("endpoint").put("endpoint", "/customers").put("method", "GET");
        var paths = evidence.putArray("dependency_paths");
        for (int index = 0; index < count; index++) {
            paths.addObject().put("service", "CustomerService")
                    .put("repository", "Repository" + index)
                    .put("resource", "Resource" + index).put("sensitivity", "HIGH");
        }
        envelope.putArray("diagnostics");
        envelope.putArray("changed_files").addObject();
        envelope.putObject("coverage").put("coverage_ratio", 1.0);
        return envelope;
    }
}
