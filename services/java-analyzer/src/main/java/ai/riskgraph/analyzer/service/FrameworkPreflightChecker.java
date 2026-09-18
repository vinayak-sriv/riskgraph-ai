package ai.riskgraph.analyzer.service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Fingerprints an acquired source snapshot for the presence of any Java source, used only when
 * a commit pair changed no {@code .java} files (see {@code AnalysisService}).
 *
 * <p>{@code GitSourceAcquirer} already filters its diff to {@code .java} paths, so whenever at
 * least one Java file changed, that file necessarily exists in one of the two snapshots and this
 * checker never runs. The ambiguous case is the other one: a commit pair with zero Java changes.
 * That is completely normal for a docs- or config-only pull request in a real Java/Spring Boot
 * repository, and today it is reported as the same {@code NO_JAVA_CHANGES} INFO diagnostic
 * regardless of cause. But it is also exactly what a non-Java repository produces on every single
 * commit pair, and in that case the analysis is not "nothing changed" but "nothing was ever
 * analyzable" — RiskGraph AI only analyzes Java 21 / Spring Boot pull requests, and that
 * distinction must not be reported as a routine, high-confidence, findings-free INFO note.
 *
 * <p>This checker only asks whether Java source exists anywhere in either snapshot; it does not
 * attempt to distinguish a plain Java project from Spring Boot specifically. That finer-grained
 * check is a deliberate fast-follow, not something this pass claims to cover.
 */
final class FrameworkPreflightChecker {

    private static final int MAX_FILES_VISITED = 20_000;

    /**
     * @return true if the snapshot could not be walked (an unrelated failure that extraction's
     *     own diagnostics, such as {@code SOURCE_ROOT_DISCOVERY_FAILED}, are better placed to
     *     report) or if at least one {@code .java} file was found; false only when the walk
     *     completed and found none.
     */
    boolean hasJavaSource(Path snapshotRoot) {
        if (snapshotRoot == null || !Files.isDirectory(snapshotRoot)) {
            return true;
        }
        JavaSourceVisitor visitor = new JavaSourceVisitor();
        try {
            Files.walkFileTree(snapshotRoot, visitor);
        } catch (IOException error) {
            return true;
        }
        return visitor.found;
    }

    private static final class JavaSourceVisitor extends SimpleFileVisitor<Path> {
        private boolean found = false;
        private int visited = 0;

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            Path name = dir.getFileName();
            if (name != null && name.toString().equals(".git")) {
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
            if (name != null && name.toString().endsWith(".java")) {
                found = true;
                return FileVisitResult.TERMINATE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException error) {
            return FileVisitResult.CONTINUE;
        }
    }
}
