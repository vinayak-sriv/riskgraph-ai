package ai.riskgraph.analyzer.extract;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Nested;
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
        assertThat(sensitivityPolicy.classify("Unknown", true).sensitivity()).isEqualTo("MEDIUM");
        assertThat(sensitivityPolicy.classify("Unknown", true).defaulted()).isTrue();
    }

    @Test
    void classifiesCompoundResourceNamesWithStructuredRules() {
        SensitivityPolicy policy = new SensitivityPolicy("");

        assertThat(policy.classify("CustomerProfile", true).sensitivity()).isEqualTo("HIGH");
        assertThat(policy.classify("CustomerExport", true).sensitivity()).isEqualTo("HIGH");
        assertThat(policy.classify("PaymentTransaction", true).sensitivity()).isEqualTo("CRITICAL");
        assertThat(policy.classify("PaymentMethod", true).sensitivity()).isEqualTo("CRITICAL");
        assertThat(policy.classify("AccessToken", true).sensitivity()).isEqualTo("CRITICAL");
        assertThat(policy.classify("UserCredential", true).sensitivity()).isEqualTo("CRITICAL");
        assertThat(policy.classify("SecretConfig", true).sensitivity()).isEqualTo("CRITICAL");
    }

    @Test
    void exactThenLongestLiteralThenDeclarationOrderDeterminesPrecedence() throws Exception {
        Path policyFile = tempDir.resolve("precedence.yml");
        Files.writeString(policyFile, """
                version: "precedence-1"
                default_sensitivity: LOW
                rules:
                  - id: broad-prefix
                    sensitivity: HIGH
                    prefix: User
                  - id: longer-suffix
                    sensitivity: CRITICAL
                    suffix: Credential
                  - id: exact-exception
                    sensitivity: LOW
                    exact: UserCredential
                """);
        SensitivityPolicy policy = new SensitivityPolicy(policyFile.toString());

        assertThat(policy.classify("UserCredential", true).sensitivity()).isEqualTo("LOW");
        assertThat(policy.classify("OtherCredential", true).sensitivity()).isEqualTo("CRITICAL");
        assertThat(policy.classify("UserProfile", true).sensitivity()).isEqualTo("HIGH");
    }

    @Test
    void supportsBoundedCaseInsensitiveRegexRules() throws Exception {
        Path policyFile = tempDir.resolve("regex.yml");
        Files.writeString(policyFile, """
                version: "regex-1"
                default_sensitivity: LOW
                rules:
                  - id: financial-record
                    sensitivity: HIGH
                    regex: "^ledger[a-z0-9_]*$"
                """);
        SensitivityPolicy policy = new SensitivityPolicy(policyFile.toString());

        assertThat(policy.classify("LedgerEntry", true).sensitivity()).isEqualTo("HIGH");
        assertThat(policy.classify("LedgerEntry", true).matchKind()).isEqualTo("regex");
    }

    @Test
    void fingerprintIncludesStructuredMatchSemantics() throws Exception {
        Path prefixPolicy = tempDir.resolve("prefix.yml");
        Path suffixPolicy = tempDir.resolve("suffix.yml");
        Files.writeString(prefixPolicy, """
                version: "fingerprint-1"
                default_sensitivity: LOW
                rules:
                  - id: customer
                    sensitivity: HIGH
                    prefix: Customer
                """);
        Files.writeString(suffixPolicy, """
                version: "fingerprint-1"
                default_sensitivity: LOW
                rules:
                  - id: customer
                    sensitivity: HIGH
                    suffix: Customer
                """);

        assertThat(new SensitivityPolicy(prefixPolicy.toString()).fingerprint())
                .isNotEqualTo(new SensitivityPolicy(suffixPolicy.toString()).fingerprint());
    }

    @Nested
    class InvalidRules {
        @Test
        void rejectsDuplicateMatchExpressions() throws Exception {
            Path policyFile = tempDir.resolve("duplicate.yml");
            Files.writeString(policyFile, """
                    version: "duplicate-1"
                    default_sensitivity: LOW
                    rules:
                      - id: first
                        sensitivity: HIGH
                        exact: Customer
                      - id: second
                        sensitivity: LOW
                        exact: customer
                    """);

            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> new SensitivityPolicy(policyFile.toString()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Duplicate sensitivity match");
        }
    }
}
