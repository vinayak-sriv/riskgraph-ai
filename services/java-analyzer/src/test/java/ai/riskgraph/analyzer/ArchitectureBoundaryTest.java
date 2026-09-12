package ai.riskgraph.analyzer;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class ArchitectureBoundaryTest {
    private static final List<String> FORBIDDEN_IMPORTS = List.of(
            "ai.riskgraph.platform",
            "org.springframework.data",
            "javax.persistence",
            "jakarta.persistence",
            "java.sql",
            "networkx",
            "ollama"
    );

    @Test
    void analyzerDoesNotCrossPlatformDatabaseGraphOrAiBoundaries() throws Exception {
        Path sourceRoot = Path.of("src/main/java");
        try (var paths = Files.walk(sourceRoot)) {
            List<Path> violations = paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> containsForbiddenImport(path))
                    .toList();
            assertThat(violations).isEmpty();
        }
    }

    private boolean containsForbiddenImport(Path path) {
        try {
            String source = Files.readString(path).toLowerCase(java.util.Locale.ROOT);
            return FORBIDDEN_IMPORTS.stream().map(value -> value.toLowerCase(java.util.Locale.ROOT))
                    .anyMatch(source::contains);
        } catch (java.io.IOException error) {
            throw new IllegalStateException(error);
        }
    }
}
