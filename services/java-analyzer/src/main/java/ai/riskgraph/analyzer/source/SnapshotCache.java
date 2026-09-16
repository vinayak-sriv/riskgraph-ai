package ai.riskgraph.analyzer.source;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SnapshotCache {
    private static final Logger LOG = LoggerFactory.getLogger(SnapshotCache.class);
    private final Path root;
    private final int maxEntries;
    private final long maxBytes;
    private final Duration maxAge;
    private final Clock clock;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Path, AtomicLong> activeLeases = new ConcurrentHashMap<>();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();

    @Autowired
    public SnapshotCache(@Value("${riskgraph.analyzer.snapshot-cache-root:}") String configuredRoot,
            @Value("${riskgraph.analyzer.snapshot-cache-max-entries:32}") int maxEntries,
            @Value("${riskgraph.analyzer.snapshot-cache-max-bytes:1073741824}") long maxBytes,
            @Value("${riskgraph.analyzer.snapshot-cache-max-age-hours:24}") long maxAgeHours) {
        this(configuredRoot.isBlank()
                        ? Path.of(System.getProperty("java.io.tmpdir"), "riskgraph-snapshot-cache")
                        : Path.of(configuredRoot), maxEntries, maxBytes,
                Duration.ofHours(Math.max(1, maxAgeHours)), Clock.systemUTC());
    }

    SnapshotCache(Path root, int maxEntries, long maxBytes, Duration maxAge, Clock clock) {
        this.root = root.toAbsolutePath().normalize();
        this.maxEntries = Math.max(1, maxEntries);
        this.maxBytes = Math.max(1, maxBytes);
        this.maxAge = maxAge;
        this.clock = clock;
    }

    Lease acquire(String repositoryIdentity, String commitSha, String limitsFingerprint,
            SnapshotWriter writer) throws IOException {
        String key = digest(repositoryIdentity + "\n" + commitSha + "\n" + limitsFingerprint);
        Object lock = locks.computeIfAbsent(key, ignored -> new Object());
        try {
            synchronized (lock) {
                Files.createDirectories(root);
                Path realRoot = root.toRealPath(LinkOption.NOFOLLOW_LINKS);
                if (Files.isSymbolicLink(root)) throw new IOException("Snapshot cache root is a symbolic link");
                Path entry = safeChild(realRoot, key);
                Path marker = entry.resolve(".complete");
                if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)
                        && Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)) {
                    hits.incrementAndGet();
                    Files.setLastModifiedTime(entry, java.nio.file.attribute.FileTime.from(clock.instant()));
                    LOG.info("snapshot_cache_hit key={} commit={}", key, commitSha);
                    return lease(entry);
                }
                if (activeLeases.containsKey(entry)) {
                    throw new IOException("Active snapshot cache entry is incomplete");
                }
                deleteTree(entry);
                misses.incrementAndGet();
                Path staging = safeChild(realRoot, "riskgraph-cache-populate-" + UUID.randomUUID());
                Files.createDirectory(staging);
                try {
                    writer.write(staging);
                    Files.writeString(staging.resolve(".complete"), key, StandardCharsets.US_ASCII);
                    try {
                        Files.move(staging, entry, StandardCopyOption.ATOMIC_MOVE);
                    } catch (AtomicMoveNotSupportedException error) {
                        Files.move(staging, entry);
                    }
                } catch (IOException | RuntimeException error) {
                    deleteTree(staging);
                    throw error;
                }
                LOG.info("snapshot_cache_miss key={} commit={}", key, commitSha);
                sweep(entry);
                return lease(entry);
            }
        } finally {
            locks.remove(key, lock);
        }
    }

    CacheStats stats() {
        return new CacheStats(hits.get(), misses.get(), evictions.get());
    }

    private Lease lease(Path entry) {
        activeLeases.computeIfAbsent(entry, ignored -> new AtomicLong()).incrementAndGet();
        return new Lease(entry, true);
    }

    void release(Path entry) {
        AtomicLong count = activeLeases.get(entry);
        if (count != null && count.decrementAndGet() <= 0) activeLeases.remove(entry, count);
    }

    private void sweep(Path protectedEntry) {
        try (var children = Files.list(root)) {
            List<Path> entries = children
                    .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().matches("[0-9a-f]{64}"))
                    .sorted(Comparator.comparing(this::lastModified)).toList();
            long totalBytes = 0;
            for (Path entry : entries) totalBytes += size(entry);
            int remaining = entries.size();
            Instant cutoff = clock.instant().minus(maxAge);
            for (Path entry : entries) {
                if (entry.equals(protectedEntry)) continue;
                if (activeLeases.containsKey(entry)) continue;
                boolean expired = lastModified(entry).isBefore(cutoff);
                if (!expired && remaining <= maxEntries && totalBytes <= maxBytes) continue;
                long entryBytes = size(entry);
                deleteTree(entry);
                remaining--;
                totalBytes -= entryBytes;
                evictions.incrementAndGet();
                LOG.info("snapshot_cache_evicted key={} bytes={}", entry.getFileName(), entryBytes);
            }
        } catch (IOException error) {
            LOG.warn("snapshot_cache_sweep_failed error_type={}", error.getClass().getSimpleName());
        }
    }

    private Path safeChild(Path realRoot, String name) throws IOException {
        Path candidate = realRoot.resolve(name).normalize();
        if (!realRoot.equals(candidate.getParent())) throw new IOException("Unsafe snapshot cache path");
        return candidate;
    }

    private Instant lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant();
        } catch (IOException error) {
            return Instant.EPOCH;
        }
    }

    private long size(Path path) throws IOException {
        try (var paths = Files.walk(path)) {
            long total = 0;
            for (Path value : paths.filter(item -> Files.isRegularFile(
                    item, LinkOption.NOFOLLOW_LINKS)).toList()) total += Files.size(value);
            return total;
        }
    }

    static void deleteTree(Path path) throws IOException {
        if (path == null || !Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            Files.deleteIfExists(path);
            return;
        }
        try (var paths = Files.walk(path)) {
            for (Path value : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(value);
            }
        }
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    @FunctionalInterface
    interface SnapshotWriter {
        void write(Path destination) throws IOException;
    }

    record Lease(Path path, boolean cached) { }
    record CacheStats(long hits, long misses, long evictions) { }
}
