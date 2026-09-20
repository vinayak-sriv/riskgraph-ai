package ai.riskgraph.platform.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryFrameworkDetectorTest {
    @TempDir Path root;
    private final RepositoryFrameworkDetector detector = new RepositoryFrameworkDetector();

    @Test void mixedJavaAndFastApiIsRejectedExplicitly() throws Exception {
        Files.writeString(root.resolve("App.java"), "class App {}");
        Files.writeString(root.resolve("tool.py"), "from fastapi import FastAPI");
        assertThat(detector.detect(root)).isEqualTo(RepositoryFrameworkDetector.Framework.MIXED);
    }

    @Test void fastApiImportWithNoJavaIsDetectedAsPython() throws Exception {
        Files.writeString(root.resolve("main.py"), "from fastapi import FastAPI\napp = FastAPI()");
        assertThat(detector.detect(root)).isEqualTo(RepositoryFrameworkDetector.Framework.PYTHON_FASTAPI);
    }

    @Test void plainPythonWithNoFastApiImportIsUnsupported() throws Exception {
        Files.writeString(root.resolve("script.py"), "print('hello')");
        assertThat(detector.detect(root)).isEqualTo(RepositoryFrameworkDetector.Framework.UNSUPPORTED);
    }

    @Test void emptyDirectoryIsUnsupported() {
        assertThat(detector.detect(root)).isEqualTo(RepositoryFrameworkDetector.Framework.UNSUPPORTED);
    }

    @Test void commitDetectionIgnoresTheCurrentWorkingTree() throws Exception {
        run("git", "init");
        run("git", "config", "user.email", "test@riskgraph.local");
        run("git", "config", "user.name", "RiskGraph Test");
        Files.writeString(root.resolve("main.py"), "from fastapi import FastAPI\napp = FastAPI()\n");
        run("git", "add", "main.py");
        run("git", "commit", "-m", "fastapi");
        String commit = run("git", "rev-parse", "HEAD").trim();
        Files.delete(root.resolve("main.py"));
        Files.writeString(root.resolve("App.java"), "class App {}");

        assertThat(detector.detect(root, commit, commit))
                .isEqualTo(RepositoryFrameworkDetector.Framework.PYTHON_FASTAPI);
    }

    private String run(String... command) throws Exception {
        Process process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor()).as(output).isZero();
        return output;
    }
}
