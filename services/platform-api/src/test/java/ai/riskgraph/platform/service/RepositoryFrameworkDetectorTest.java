package ai.riskgraph.platform.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryFrameworkDetectorTest {
    @TempDir Path root;
    private final RepositoryFrameworkDetector detector = new RepositoryFrameworkDetector();

    @Test void javaSourceWinsEvenAlongsideAFastApiImport() throws Exception {
        Files.writeString(root.resolve("App.java"), "class App {}");
        Files.writeString(root.resolve("tool.py"), "from fastapi import FastAPI");
        assertThat(detector.detect(root)).isEqualTo(RepositoryFrameworkDetector.Framework.JAVA_SPRING);
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
}
