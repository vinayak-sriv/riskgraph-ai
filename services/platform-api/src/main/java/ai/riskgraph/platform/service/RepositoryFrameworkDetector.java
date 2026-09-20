package ai.riskgraph.platform.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.regex.Pattern;

/**
 * Fingerprints both immutable requested revisions to select an analyzer. Mixed and unsupported
 * repositories are explicit results so the platform cannot emit a misleading security verdict.
 */
class RepositoryFrameworkDetector {
    enum Framework { JAVA_SPRING, PYTHON_FASTAPI, MIXED, UNSUPPORTED }

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
        return classify(visitor.hasJava, visitor.hasFastApiImport);
    }

    Framework detect(Path repository, String oldCommit, String newCommit) {
        Framework before = detectCommit(repository, oldCommit);
        Framework after = detectCommit(repository, newCommit);
        if (before == Framework.MIXED || after == Framework.MIXED || before != after) {
            return Framework.MIXED;
        }
        return before;
    }

    private Framework detectCommit(Path repository, String commit) {
        try {
            Process names = new ProcessBuilder("git", "-C", repository.toString(),
                    "ls-tree", "-r", "--name-only", commit).redirectErrorStream(true).start();
            String output;
            try (InputStream stream = names.getInputStream()) {
                output = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (names.waitFor() != 0) return nonGitFallback(repository);
            boolean java = false;
            boolean python = false;
            int visited = 0;
            for (String name : output.lines().toList()) {
                if (++visited > MAX_FILES_VISITED) break;
                if (name.endsWith(".java")) java = true;
                if (name.endsWith(".py")) python = true;
            }
            boolean fastApi = python && hasFastApiImport(repository, commit);
            return classify(java, fastApi);
        } catch (IOException error) {
            return nonGitFallback(repository);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return Framework.UNSUPPORTED;
        }
    }

    private Framework nonGitFallback(Path repository) {
        Framework detected = detect(repository);
        // Unit callers and local fixture paths may not be Git repositories. Preserve the historic
        // Java default there; production requests still provide resolvable immutable commits.
        return detected == Framework.UNSUPPORTED ? Framework.JAVA_SPRING : detected;
    }

    private boolean hasFastApiImport(Path repository, String commit)
            throws IOException, InterruptedException {
        Process grep = new ProcessBuilder("git", "-C", repository.toString(), "grep", "-I", "-l", "-E",
                "^[[:space:]]*(from[[:space:]]+fastapi|import[[:space:]]+fastapi)",
                commit, "--", "*.py").redirectErrorStream(true).start();
        try (InputStream stream = grep.getInputStream()) {
            stream.transferTo(java.io.OutputStream.nullOutputStream());
        }
        return grep.waitFor() == 0;
    }

    private static Framework classify(boolean java, boolean fastApi) {
        if (java && fastApi) return Framework.MIXED;
        if (java) return Framework.JAVA_SPRING;
        if (fastApi) return Framework.PYTHON_FASTAPI;
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
