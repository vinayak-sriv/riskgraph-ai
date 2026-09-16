package ai.riskgraph.platform.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ScanIdentityFactoryTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void identityIgnoresPropertyListAndPostAnalysisOutputOrdering() throws Exception {
        JsonNode envelope = envelope("analyzer-config");
        JsonNode firstInput = mapper.readTree("""
                {"before":[{"method":"GET","endpoint":"/a"}],
                 "after":[{"endpoint":"/b","method":"POST"},{"endpoint":"/a","method":"GET"}],
                 "quality":{"confidence":"HIGH","incomplete":false,"coverage_ratio":1.0}}
                """);
        JsonNode reorderedInput = mapper.readTree("""
                {"quality":{"coverage_ratio":1.0,"incomplete":false,"confidence":"HIGH"},
                 "after":[{"method":"GET","endpoint":"/a"},{"method":"POST","endpoint":"/b"}],
                 "before":[{"endpoint":"/a","method":"GET"}]}
                """);
        JsonNode firstResult = mapper.readTree(
                "{\"risk_result\":{\"policy_version\":\"1.0.0\"},\"validation\":{\"status\":\"CONFIRMED\"}}");
        JsonNode changedOutput = mapper.readTree(
                "{\"ai\":{\"provider\":\"different\"},\"risk_result\":{\"policy_version\":\"1.0.0\"}}");

        assertThat(ScanIdentityFactory.create(mapper, envelope, firstInput, firstResult))
                .isEqualTo(ScanIdentityFactory.create(
                        mapper, envelope, reorderedInput, changedOutput));
    }

    @Test
    void everySemanticAnalyzerRevisionAndPolicyInputChangesIdentity() throws Exception {
        JsonNode input = mapper.readTree("{\"before\":[],\"after\":[]}");
        JsonNode policyOne = mapper.readTree(
                "{\"risk_result\":{\"policy_version\":\"1.0.0\"}}");
        JsonNode policyTwo = mapper.readTree(
                "{\"risk_result\":{\"policy_version\":\"2.0.0\"}}");
        String baseline = ScanIdentityFactory.create(mapper, envelope("config-a"), input, policyOne);

        assertThat(ScanIdentityFactory.create(mapper, envelope("config-b"), input, policyOne))
                .isNotEqualTo(baseline);
        assertThat(ScanIdentityFactory.create(mapper, envelope("config-a"),
                mapper.readTree("{\"before\":[],\"after\":[{\"endpoint\":\"/new\"}]}"),
                policyOne)).isNotEqualTo(baseline);
        assertThat(ScanIdentityFactory.create(mapper, envelope("config-a"), input, policyTwo))
                .isNotEqualTo(baseline);
    }

    private JsonNode envelope(String config) throws Exception {
        return mapper.readTree("""
                {"schema_version":"1.1.0","analyzer_version":"0.4.1",
                 "analyzer_config_hash":"%s","provenance":{
                   "repository_identity":"local:test","old_commit":"%s","new_commit":"%s"}}
                """.formatted(config, "a".repeat(40), "b".repeat(40)));
    }
}
