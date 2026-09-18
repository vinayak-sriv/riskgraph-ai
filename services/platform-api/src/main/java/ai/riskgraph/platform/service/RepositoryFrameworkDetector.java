package ai.riskgraph.platform.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.regex.Pattern;

/**
 * Fingerprints a repository snapshot to pick which analyzer to route to. Java always wins when
 * present (today's only supported case, and the existing test/production repositories never
 * carry a Python marker), so this is purely additive: it can only ever redirect a repository that
 * previously had no analyzer path at all. It does not reject anything (that belongs to the
 * broader framework-preflight work tracked separately) — {@code UNSUPPORTED} still routes to the
 * Java analyzer unchanged, matching current behavior for an empty or non-source directory.
 */
final class RepositoryFrameworkDetector {
    enum Framework { JAVA_SPRING, PYTHON_FASTAPI, UNSUPPORTED }

    private static final int MAX_FILES_VISITED = 20_000;
    private static final long MAX_PY_FILE_BYTES = 1_048_576;
    private static final Pattern FASTAPI_IMPORT =
            Pattern.compile("(?m)^\\s*(from\\s+fastapi\\b|import\\s+fastapi\\b)");

    Framework detect(Path snapshotRoot) {
        if (snapshotRoot == null || !Files.isDirectory(snapshotRoot)) {
            return Framework.UNSUPPORTED;
        }
        Visitor visitor = new Visitor();
        try {
            Files.walkFileTree(snapshotRoot, visitor);
        } catch (IOException error) {
            return Framework.UNSUPPORTED;
        }
        if (visitor.hasJava) return Framework.JAVA_SPRING;
        if (visitor.hasFastApiImport) return Framework.PYTHON_FASTAPI;
        return Framework.UNSUPPORTED;
    }

    private static final class Visitor extends SimpleFileVisitor<Path> {
        private boolean hasJava = false;
        private boolean hasFastApiImport = false;
        private int visited = 0;

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            Path name = dir.getFileName();
            String value = name == null ? "" : name.toString();
            if (value.equals(".git") || value.equals("node_modules")
                    || value.equals(".venv") || value.equals("venv")) {
                return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (++visited > MAX_FILES_VISITED) {
                return FileVisitResult.TERMINATE;
            }
            Path name = file.getFileName();
            String value = name == null ? "" : name.toString();
            if (value.endsWith(".java")) {
                hasJava = true;
                return FileVisitResult.TERMINATE; // Java always wins; stop looking.
            }
            if (!hasFastApiImport && value.endsWith(".py") && attrs.size() <= MAX_PY_FILE_BYTES) {
                try {
                    if (FASTAPI_IMPORT.matcher(Files.readString(file, StandardCharsets.UTF_8)).find()) {
                        hasFastApiImport = true;
                    }
                } catch (IOException | UncheckedIOException ignored) {
                    // Unreadable or non-UTF-8 file; inconclusive for this one file, keep walking.
                }
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException error) {
            return FileVisitResult.CONTINUE;
        }
    }
}
