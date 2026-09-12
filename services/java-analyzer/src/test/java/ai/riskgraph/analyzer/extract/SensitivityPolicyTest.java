package ai.riskgraph.analyzer.extract;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SensitivityPolicyTest {
    @TempDir
    Path tempDir;

    @Test
    void appliesConfiguredExactRuleAndDocumentedFallback() throws Exception {
        Path policy = tempDir.resolve("policy.yml");
        Files.writeString(policy, """
                version: "test-1"
                default_sensitivity: LOW
                resources:
                  Invoice: CRITICAL
                """);

        SensitivityPolicy sensitivityPolicy = new SensitivityPolicy(policy.toString());

        assertThat(sensitivityPolicy.classify("Invoice").sensitivity()).isEqualTo("CRITICAL");
        assertThat(sensitivityPolicy.classify("Invoice").matchedRule()).isEqualTo("Invoice@test-1");
        assertThat(sensitivityPolicy.classify("Unknown").sensitivity()).isEqualTo("LOW");
        assertThat(sensitivityPolicy.classify("Unknown").matchedRule()).isEqualTo("default@test-1");
    }
}
