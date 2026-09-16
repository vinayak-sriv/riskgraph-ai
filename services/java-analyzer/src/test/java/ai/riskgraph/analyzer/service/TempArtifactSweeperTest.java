package ai.riskgraph.analyzer.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TempArtifactSweeperTest {
    private static final Instant NOW = Instant.parse("2026-09-16T00:00:00Z");

    @Test
    void removesOnlyOldDirectChildrenWithOwnedPrefixes(@TempDir Path tempRoot) throws Exception {
        Path oldOwned = Files.createDirectory(tempRoot.resolve("riskgraph-old-abandoned"));
        Files.writeString(oldOwned.resolve("Controller.java"), "class Controller {}");
        Path recentOwned = Files.createFile(tempRoot.resolve("riskgraph-analysis-current.log"));
        Path unrelated = Files.createDirectory(tempRoot.resolve("important-user-data"));
        FileTime old = FileTime.from(NOW.minus(Duration.ofDays(2)));
        Files.setLastModifiedTime(oldOwned, old);
        Files.setLastModifiedTime(unrelated, old);
        Files.setLastModifiedTime(recentOwned, FileTime.from(NOW));
        TempArtifactSweeper sweeper = new TempArtifactSweeper(tempRoot,
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofHours(24), 10);

        TempArtifactSweeper.SweepResult result = sweeper.sweep();

        assertThat(oldOwned).doesNotExist();
        assertThat(recentOwned).exists();
        assertThat(unrelated).exists();
        assertThat(result.removed()).isEqualTo(1);
        assertThat(result.failed()).isZero();
    }

    @Test
    void boundsTheNumberOfCandidatesExamined(@TempDir Path tempRoot) throws Exception {
        for (int index = 0; index < 5; index++) {
            Path artifact = Files.createFile(tempRoot.resolve("riskgraph-analysis-" + index + ".log"));
            Files.setLastModifiedTime(artifact, FileTime.from(NOW.minus(Duration.ofDays(2))));
        }
        TempArtifactSweeper sweeper = new TempArtifactSweeper(tempRoot,
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofHours(24), 2);

        TempArtifactSweeper.SweepResult result = sweeper.sweep();

        assertThat(result.examined()).isEqualTo(2);
        assertThat(result.removed()).isEqualTo(2);
        assertThat(Files.list(tempRoot).count()).isEqualTo(3);
    }
}
