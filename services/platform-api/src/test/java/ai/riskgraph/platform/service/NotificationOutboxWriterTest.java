package ai.riskgraph.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class NotificationOutboxWriterTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final NotificationOutboxWriter writer =
            new NotificationOutboxWriter(db, mapper, new ContractValidator());

    @Test
    void writesNoRowForAnAllowVerdict() {
        writer.write(1L, scanResult("ALLOW"));
        verify(db, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void writesADedupedReviewOrBlockEventMatchingTheContracts() {
        writer.write(7L, scanResult("BLOCK"));

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(db).update(anyString(), args.capture());
        Object[] params = args.getValue();
        assertThat(params[1]).isEqualTo(7L);
        assertThat(params[2].toString()).startsWith("decision:").hasSize(73);
        assertThat(params[3]).isEqualTo("BLOCK");
        // The contract-validated event payload round-trips as valid JSON.
        JsonNode event = mapper.readTree((String) params[4]);
        assertThat(event.path("event_type").asString()).isEqualTo("final-decision");
        assertThat(event.path("decision").path("final_verdict").asString()).isEqualTo("BLOCK");
        assertThat(event.at("/decision/pull_request/number").asInt()).isEqualTo(69);
        assertThat(event.at("/decision/pull_request/url").asString())
                .isEqualTo("https://github.com/demo/riskgraph-sample/pull/69");
        assertThat(event.at("/decision/finding_fingerprints/0").asString()).hasSize(64);
    }

    private JsonNode scanResult(String verdict) {
        ObjectNode result = mapper.createObjectNode();
        result.put("scan_id", "scan-abc");
        result.put("final_verdict", verdict);
        result.put("validation_status", "NOT_RUN");
        result.putObject("pull_request")
                .put("number", 69)
                .put("url", "https://github.com/demo/riskgraph-sample/pull/69");
        ObjectNode provenance = result.putObject("provenance");
        provenance.put("repository_identity", "demo/riskgraph-sample");
        provenance.put("old_commit", "0f6e8614047bd74cf6223b4d8a858d2ed2824f8a");
        provenance.put("new_commit", "bb37aad8c332264723817d855e8b3b96b7c392bc");
        ObjectNode riskResult = result.putObject("risk_result");
        riskResult.put("risk_before", 22);
        riskResult.put("risk_after", 91);
        riskResult.put("risk_delta", 69);
        riskResult.put("category_after", "CRITICAL");
        result.putObject("quality").put("confidence", "HIGH");
        result.putArray("findings").addObject().put("route_id", "endpoint:GET:/admin/export")
                .put("resource", "Customer");
        return result;
    }
}
