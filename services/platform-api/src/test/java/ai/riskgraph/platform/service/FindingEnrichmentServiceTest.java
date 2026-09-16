package ai.riskgraph.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class FindingEnrichmentServiceTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void findingUsesItsOwnRiskAndPreservesAmbiguousHandlerIdentities() {
        ObjectNode result = mapper.createObjectNode().put("scan_id", "a".repeat(64))
                .put("status", "COMPLETE");
        result.putObject("provenance").put("repository_identity", "local:test");
        result.putObject("quality").put("confidence", "HIGH").put("incomplete", false);
        result.putArray("diagnostics");
        ObjectNode delta = result.putObject("graph_delta");
        ArrayNode paths = delta.putArray("new_paths");
        ObjectNode path = paths.addObject().put("source", "user:anonymous")
                .put("target", "resource:Customer");
        path.putArray("nodes").add("user:anonymous")
                .add("endpoint:GET:/customers").add("resource:Customer");
        path.putArray("edges").add("CAN_ACCESS").add("RETURNS");
        ObjectNode risk = result.putObject("risk_result");
        ObjectNode findingRisk = risk.putArray("finding_results").addObject()
                .put("route_id", "endpoint:GET:/customers")
                .put("resource_id", "resource:Customer")
                .put("category_after", "HIGH");
        findingRisk.putArray("evidence").add("Finding-specific high risk evidence");
        ObjectNode sourceEvidence = result.putObject("source_evidence");
        sourceEvidence.putArray("before");
        ArrayNode after = sourceEvidence.putArray("after");
        after.add(handler("demo.FirstController", "customers()", "FirstController.java"));
        after.add(handler("demo.SecondController", "customers()", "SecondController.java"));

        new FindingEnrichmentService(mock(ai.riskgraph.platform.client.AnalysisClient.class),
                mock(ContractValidator.class), mapper).buildFindings(result);

        assertThat(result.at("/findings/0/severity").asString()).isEqualTo("HIGH");
        assertThat(result.at("/findings/0/evidence/0").asString())
                .isEqualTo("Finding-specific high risk evidence");
        assertThat(result.at("/findings/0/handler_refs")).hasSize(2);
        assertThat(result.at("/findings/0/handler_refs/0/handler_id").asString())
                .hasSize(64).isNotEqualTo(
                        result.at("/findings/0/handler_refs/1/handler_id").asString());
        assertThat(result.at("/diagnostics/0/code").asString())
                .isEqualTo("AMBIGUOUS_ROUTE_HANDLERS");
        assertThat(result.path("status").asString()).isEqualTo("DEGRADED");
        assertThat(result.at("/quality/confidence").asString()).isEqualTo("MEDIUM");
        assertThat(result.at("/quality/incomplete").asBoolean()).isTrue();
    }

    private ObjectNode handler(String controller, String signature, String path) {
        ObjectNode source = mapper.createObjectNode();
        source.putObject("endpoint").put("method", "GET").put("endpoint", "/customers")
                .put("resource", "Customer");
        source.put("qualified_controller", controller).put("method_signature", signature);
        source.putObject("source_location").put("path", path)
                .put("start_line", 10).put("end_line", 12);
        source.putArray("dependency_paths");
        return source;
    }
}
