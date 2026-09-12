package ai.riskgraph.analyzer.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitSourceAcquirerTest {
    @TempDir
    Path tempDir;

    @Test
    void acquiresDeterministicDiffAndCleansSnapshots() throws Exception {
        Path repository = tempDir.resolve("repository");
        Files.createDirectories(repository);
        String oldCommit;
        String newCommit;
        try (Git git = Git.init().setDirectory(repository.toFile()).call()) {
            write(repository, "src/App.java", "class App {\n  int value = 1;\n}\n");
            git.add().addFilepattern(".").call();
            oldCommit = git.commit().setMessage("before").setAuthor("RiskGraph", "test@riskgraph.ai").call().getName();
            write(repository, "src/App.java", "class App {\n  int value = 2;\n}\n");
            git.add().addFilepattern(".").call();
            newCommit = git.commit().setMessage("after").setAuthor("RiskGraph", "test@riskgraph.ai").call().getName();
        }

        GitSourceAcquirer acquirer = new GitSourceAcquirer(tempDir.toString());
        Path oldSnapshot;
        Path newSnapshot;
        try (var acquired = acquirer.acquire(repository, oldCommit, newCommit)) {
            oldSnapshot = acquired.oldSnapshot();
            newSnapshot = acquired.newSnapshot();
            assertThat(acquired.changedFiles()).hasSize(1);
            assertThat(acquired.changedFiles().getFirst().status()).isEqualTo("MODIFY");
            assertThat(acquired.changedFiles().getFirst().old_path()).isEqualTo("src/App.java");
            assertThat(acquired.changedFiles().getFirst().old_ranges()).isNotEmpty();
            assertThat(Files.readString(oldSnapshot.resolve("src/App.java"))).contains("value = 1");
            assertThat(Files.readString(newSnapshot.resolve("src/App.java"))).contains("value = 2");
        }
        assertThat(oldSnapshot).doesNotExist();
        assertThat(newSnapshot).doesNotExist();
    }

    @Test
    void rejectsAbbreviatedCommitBeforeReadingObjects() throws Exception {
        Path repository = tempDir.resolve("repository");
        Files.createDirectories(repository);
        try (Git ignored = Git.init().setDirectory(repository.toFile()).call()) {
            GitSourceAcquirer acquirer = new GitSourceAcquirer(tempDir.toString());
            assertThatThrownBy(() -> acquirer.acquire(repository, "abc1234", "def5678"))
                    .isInstanceOf(SourceAcquisitionException.class)
                    .extracting(error -> ((SourceAcquisitionException) error).code())
                    .isEqualTo("INVALID_COMMIT");
        }
    }

    @Test
    void rejectsRepositoryOutsideAllowlist() throws Exception {
        Path repository = tempDir.resolve("repository");
        Path allowed = tempDir.resolve("allowed");
        Files.createDirectories(repository);
        Files.createDirectories(allowed);
        GitSourceAcquirer acquirer = new GitSourceAcquirer(allowed.toString());

        assertThatThrownBy(() -> acquirer.acquire(repository, "a".repeat(40), "b".repeat(40)))
                .isInstanceOf(SourceAcquisitionException.class)
                .extracting(error -> ((SourceAcquisitionException) error).code())
                .isEqualTo("REPOSITORY_NOT_ALLOWED");
    }

    @Test
    void reportsRenameDeletionNoChangeAndIgnoresNonJavaChanges() throws Exception {
        Path repository = tempDir.resolve("repository");
        Files.createDirectories(repository);
        String initial;
        String renamed;
        String nonJava;
        String deleted;
        try (Git git = Git.init().setDirectory(repository.toFile()).call()) {
            write(repository, "src/Original.java", "class Original {}\n");
            write(repository, "README.md", "initial\n");
            git.add().addFilepattern(".").call();
            initial = git.commit().setMessage("initial").setAuthor("RiskGraph", "test@riskgraph.ai").call().getName();

            Files.move(repository.resolve("src/Original.java"), repository.resolve("src/Renamed.java"));
            git.add().addFilepattern(".").call();
            git.rm().addFilepattern("src/Original.java").call();
            renamed = git.commit().setMessage("rename").setAuthor("RiskGraph", "test@riskgraph.ai").call().getName();

            write(repository, "README.md", "documentation only\n");
            git.add().addFilepattern(".").call();
            nonJava = git.commit().setMessage("docs").setAuthor("RiskGraph", "test@riskgraph.ai").call().getName();

            git.rm().addFilepattern("src/Renamed.java").call();
            deleted = git.commit().setMessage("delete").setAuthor("RiskGraph", "test@riskgraph.ai").call().getName();
        }

        GitSourceAcquirer acquirer = new GitSourceAcquirer(tempDir.toString());
        try (var result = acquirer.acquire(repository, initial, renamed)) {
            assertThat(result.changedFiles()).singleElement().satisfies(file -> {
                assertThat(file.status()).isEqualTo("RENAME");
                assertThat(file.old_path()).isEqualTo("src/Original.java");
                assertThat(file.new_path()).isEqualTo("src/Renamed.java");
            });
        }
        try (var result = acquirer.acquire(repository, renamed, nonJava)) {
            assertThat(result.changedFiles()).isEmpty();
        }
        try (var result = acquirer.acquire(repository, nonJava, deleted)) {
            assertThat(result.changedFiles()).singleElement().satisfies(file -> {
                assertThat(file.status()).isEqualTo("DELETE");
                assertThat(file.new_path()).isNull();
            });
        }
        try (var result = acquirer.acquire(repository, initial, initial)) {
            assertThat(result.changedFiles()).isEmpty();
        }
    }

    @Test
    void rejectsUnknownFullCommitSha() throws Exception {
        Path repository = tempDir.resolve("repository");
        Files.createDirectories(repository);
        try (Git ignored = Git.init().setDirectory(repository.toFile()).call()) {
            GitSourceAcquirer acquirer = new GitSourceAcquirer(tempDir.toString());
            assertThatThrownBy(() -> acquirer.acquire(repository, "a".repeat(40), "b".repeat(40)))
                    .isInstanceOf(SourceAcquisitionException.class)
                    .extracting(error -> ((SourceAcquisitionException) error).code())
                    .isEqualTo("INVALID_COMMIT");
        }
    }

    @Test
    void rejectsJavaBlobOverConfiguredLimit() throws Exception {
        Path repository = tempDir.resolve("large-repository");
        Files.createDirectories(repository);
        String commit;
        try (Git git = Git.init().setDirectory(repository.toFile()).call()) {
            write(repository, "src/Large.java", "class Large { /* " + "x".repeat(200) + " */ }");
            git.add().addFilepattern(".").call();
            commit = git.commit().setMessage("large source")
                    .setAuthor("RiskGraph", "test@riskgraph.ai").call().getName();
        }

        GitSourceAcquirer acquirer = new GitSourceAcquirer(tempDir.toString(), 10, 64, 1024);
        assertThatThrownBy(() -> acquirer.acquire(repository, commit, commit))
                .isInstanceOf(SourceAcquisitionException.class)
                .extracting(error -> ((SourceAcquisitionException) error).code())
                .isEqualTo("SOURCE_LIMIT_EXCEEDED");
    }

    @Test
    void rejectsUnmaterializedGitLfsJavaPointer() throws Exception {
        Path repository = tempDir.resolve("lfs-repository");
        Files.createDirectories(repository);
        String commit;
        try (Git git = Git.init().setDirectory(repository.toFile()).call()) {
            write(repository, "src/Pointer.java", """
                    version https://git-lfs.github.com/spec/v1
                    oid sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                    size 1024
                    """);
            git.add().addFilepattern(".").call();
            commit = git.commit().setMessage("lfs pointer")
                    .setAuthor("RiskGraph", "test@riskgraph.ai").call().getName();
        }

        GitSourceAcquirer acquirer = new GitSourceAcquirer(tempDir.toString());
        assertThatThrownBy(() -> acquirer.acquire(repository, commit, commit))
                .isInstanceOf(SourceAcquisitionException.class)
                .extracting(error -> ((SourceAcquisitionException) error).code())
                .isEqualTo("GIT_LFS_POINTER");
    }

    private void write(Path repository, String relativePath, String contents) throws Exception {
        Path file = repository.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, contents);
    }
}
