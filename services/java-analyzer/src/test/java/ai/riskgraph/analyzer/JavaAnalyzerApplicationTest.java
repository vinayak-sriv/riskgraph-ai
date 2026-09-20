package ai.riskgraph.analyzer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// The repository allowlist has no default, so the context needs one explicitly.
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "riskgraph.analyzer.allowed-repository-roots=${java.io.tmpdir}")
class JavaAnalyzerApplicationTest {
    @Test
    void contextLoads() {
    }
}
