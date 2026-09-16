package ai.riskgraph.analyzer.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotCacheTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-16T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void reusesOnlyCompleteEntriesWithTheSameIdentityCommitAndLimits(@TempDir Path root)
            throws Exception {
        SnapshotCache cache = new SnapshotCache(root, 4, 1024 * 1024,
                Duration.ofHours(1), CLOCK);
        AtomicInteger writes = new AtomicInteger();
        SnapshotCache.SnapshotWriter writer = destination -> {
            writes.incrementAndGet();
            Files.writeString(destination.resolve("Controller.java"), "class Controller {}");
        };

        SnapshotCache.Lease first = cache.acquire("repository", "a".repeat(40),
                "5000:1024:4096", writer);
        SnapshotCache.Lease hit = cache.acquire("repository", "a".repeat(40),
                "5000:1024:4096", writer);
        SnapshotCache.Lease changedLimits = cache.acquire("repository", "a".repeat(40),
                "100:1024:4096", writer);

        assertThat(hit.path()).isEqualTo(first.path());
        assertThat(changedLimits.path()).isNotEqualTo(first.path());
        assertThat(writes).hasValue(2);
        assertThat(cache.stats()).isEqualTo(new SnapshotCache.CacheStats(1, 2, 0));
        cache.release(first.path());
        cache.release(hit.path());
        cache.release(changedLimits.path());
    }

    @Test
    void incompleteEntryIsNeverAHit(@TempDir Path root) throws Exception {
        SnapshotCache cache = new SnapshotCache(root, 4, 1024 * 1024,
                Duration.ofHours(1), CLOCK);
        AtomicInteger writes = new AtomicInteger();
        SnapshotCache.SnapshotWriter writer = destination -> {
            writes.incrementAndGet();
            Files.writeString(destination.resolve("Controller.java"), "class Controller {}");
        };
        SnapshotCache.Lease first = cache.acquire("repository", "b".repeat(40), "limits", writer);
        cache.release(first.path());
        Files.delete(first.path().resolve(".complete"));

        SnapshotCache.Lease rebuilt = cache.acquire(
                "repository", "b".repeat(40), "limits", writer);

        assertThat(rebuilt.path().resolve(".complete")).exists();
        assertThat(writes).hasValue(2);
        assertThat(cache.stats().hits()).isZero();
        cache.release(rebuilt.path());
    }

    @Test
    void evictsOutsideTheBoundWithoutDeletingTheCurrentEntry(@TempDir Path root)
            throws Exception {
        SnapshotCache cache = new SnapshotCache(root, 1, 1024 * 1024,
                Duration.ofHours(1), CLOCK);
        SnapshotCache.Lease first = cache.acquire("repository", "c".repeat(40), "limits",
                destination -> Files.writeString(destination.resolve("First.java"), "class First {}"));
        cache.release(first.path());
        SnapshotCache.Lease second = cache.acquire("repository", "d".repeat(40), "limits",
                destination -> Files.writeString(destination.resolve("Second.java"), "class Second {}"));

        assertThat(first.path()).doesNotExist();
        assertThat(second.path()).exists();
        assertThat(cache.stats().evictions()).isEqualTo(1);
    }
}
