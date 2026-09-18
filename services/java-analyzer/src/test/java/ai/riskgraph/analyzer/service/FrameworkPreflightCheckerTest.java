package ai.riskgraph.analyzer.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FrameworkPreflightCheckerTest {
    private final FrameworkPreflightChecker checker = new FrameworkPreflightChecker();

    @TempDir
    Path tempDir;

    @Test
    void findsJavaSourceInAConventionalLayout() throws Exception {
        Path file = tempDir.resolve("src/main/java/demo/Controller.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "package demo; class Controller {}");

        assertThat(checker.hasJavaSource(tempDir)).isTrue();
    }

    @Test
    void findsJavaSourceInAnUnconventionalLayout() throws Exception {
        Path file = tempDir.resolve("lib/Controller.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "class Controller {}");

        assertThat(checker.hasJavaSource(tempDir)).isTrue();
    }

    @Test
    void reportsNoJavaSourceForANonJavaRepository() throws Exception {
        Path file = tempDir.resolve("app.py");
        Files.writeString(file, "print('hello')");

        assertThat(checker.hasJavaSource(tempDir)).isFalse();
    }

    @Test
    void ignoresTheGitDirectoryWhenLookingForJavaSource() throws Exception {
        Path gitFile = tempDir.resolve(".git/objects/whatever.java");
        Files.createDirectories(gitFile.getParent());
        Files.writeString(gitFile, "not real source");
        Path readme = tempDir.resolve("README.md");
        Files.writeString(readme, "docs only");

        assertThat(checker.hasJavaSource(tempDir)).isFalse();
    }

    @Test
    void treatsAMissingSnapshotAsInconclusiveRatherThanUnsupported() {
        assertThat(checker.hasJavaSource(tempDir.resolve("does-not-exist"))).isTrue();
    }
}
