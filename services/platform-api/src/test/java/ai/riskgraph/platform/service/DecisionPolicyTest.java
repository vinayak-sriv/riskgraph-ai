package ai.riskgraph.platform.service;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class DecisionPolicyTest {
    @Test void failedValidationNeverDowngradesBlock() {
        for (String status : new String[]{"CONFIRMED","REJECTED","INCONCLUSIVE","NOT_RUN","ERROR","unknown"})
            assertThat(DecisionPolicy.finalVerdict("BLOCK", status)).isEqualTo("BLOCK");
    }
    @Test void nonBlockDecisionsFailClosed() {
        assertThat(DecisionPolicy.finalVerdict("ALLOW","NOT_RUN")).isEqualTo("ALLOW");
        assertThat(DecisionPolicy.finalVerdict("ALLOW","ERROR")).isEqualTo("REVIEW");
        assertThat(DecisionPolicy.finalVerdict("REVIEW","REJECTED")).isEqualTo("REVIEW");
        assertThat(DecisionPolicy.finalVerdict("REVIEW","CONFIRMED")).isEqualTo("BLOCK");
        assertThat(DecisionPolicy.finalVerdict("invalid","NOT_RUN")).isEqualTo("REVIEW");
    }
}
