package ai.riskgraph.analyzer.source;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
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
    private final java.util.Map<Path, ProcessLease> activeLeases = new java.util.HashMap<>();
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
        Path realRoot = prepareRoot();
        Path lockRoot = realRoot.resolve(".locks");
        Path populateLock = safeChild(lockRoot, key + ".populate.lock");
        try (FileChannel channel = FileChannel.open(populateLock,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock ignored = channel.lock()) {
            Path entry = safeChild(realRoot, key);
            Lease lease = lease(entry, lockRoot);
            boolean success = false;
            try {
                Path marker = entry.resolve(".complete");
                if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)
                        && Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)) {
                    hits.incrementAndGet();
                    Files.setLastModifiedTime(entry, java.nio.file.attribute.FileTime.from(clock.instant()));
                    LOG.info("snapshot_cache_hit key={} commit={}", key, commitSha);
                    success = true;
                    return lease;
                }
                deleteTree(entry);
                misses.incrementAndGet();
                Path staging = safeChild(realRoot, "riskgraph-cache-populate-" + UUID.randomUUID());
                Files.createDirectory(staging);
                try {
                    writer.write(staging);
                    Files.writeString(staging.resolve(".complete"),
                            key + "\n" + walkSize(staging), StandardCharsets.US_ASCII);
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
                sweep(entry, lockRoot);
                success = true;
                return lease;
            } finally {
                if (!success) release(entry);
            }
        }
    }

    CacheStats stats() {
        return new CacheStats(hits.get(), misses.get(), evictions.get());
    }

    private synchronized Lease lease(Path entry, Path lockRoot) throws IOException {
        ProcessLease processLease = activeLeases.get(entry);
        if (processLease == null) {
            Path lockPath = safeChild(lockRoot, entry.getFileName() + ".lease.lock");
            FileChannel channel = FileChannel.open(lockPath,
                    StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
            try {
                processLease = new ProcessLease(channel, channel.lock(0, Long.MAX_VALUE, true), 0);
                activeLeases.put(entry, processLease);
            } catch (IOException | RuntimeException error) {
                channel.close();
                throw error;
            }
        }
        processLease.references++;
        return new Lease(entry, true);
    }

    synchronized void release(Path entry) {
        ProcessLease processLease = activeLeases.get(entry);
        if (processLease == null || --processLease.references > 0) return;
        activeLeases.remove(entry);
        try {
            processLease.lock.close();
            processLease.channel.close();
        } catch (IOException error) {
            LOG.warn("snapshot_cache_lease_release_failed key={} error_type={}",
                    entry.getFileName(), error.getClass().getSimpleName());
        }
    }

    private void sweep(Path protectedEntry, Path lockRoot) {
        Path sweepPath;
        try {
            sweepPath = safeChild(lockRoot, "sweep.lock");
        } catch (IOException error) {
            return;
        }
        try (FileChannel sweepChannel = FileChannel.open(sweepPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock ignored = sweepChannel.lock()) {
            sweepEntries(protectedEntry, lockRoot);
        } catch (IOException | OverlappingFileLockException error) {
            LOG.warn("snapshot_cache_sweep_failed error_type={}", error.getClass().getSimpleName());
        }
    }

    private void sweepEntries(Path protectedEntry, Path lockRoot) {
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
                boolean expired = lastModified(entry).isBefore(cutoff);
                if (!expired && remaining <= maxEntries && totalBytes <= maxBytes) continue;
                try (ExclusiveLease exclusive = tryExclusiveLease(entry, lockRoot)) {
                    if (exclusive == null) continue;
                    long entryBytes = size(entry);
                    deleteTree(entry);
                    remaining--;
                    totalBytes -= entryBytes;
                    evictions.incrementAndGet();
                    LOG.info("snapshot_cache_evicted key={} bytes={}", entry.getFileName(), entryBytes);
                }
            }
        } catch (IOException error) {
            LOG.warn("snapshot_cache_sweep_failed error_type={}", error.getClass().getSimpleName());
        }
    }

    private ExclusiveLease tryExclusiveLease(Path entry, Path lockRoot) throws IOException {
        Path lockPath = safeChild(lockRoot, entry.getFileName() + ".lease.lock");
        FileChannel channel = FileChannel.open(lockPath,
                StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) {
                channel.close();
                return null;
            }
            return new ExclusiveLease(channel, lock);
        } catch (OverlappingFileLockException error) {
            channel.close();
            return null;
        }
    }

    private Path prepareRoot() throws IOException {
        Files.createDirectories(root);
        if (Files.isSymbolicLink(root)) throw new IOException("Snapshot cache root is a symbolic link");
        Path realRoot = root.toRealPath(LinkOption.NOFOLLOW_LINKS);
        Files.createDirectories(realRoot.resolve(".locks"));
        return realRoot;
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

    /**
     * Entry size, from the {@code .complete} marker when it records one.
     *
     * <p>The sweep runs on every cache miss and used to walk every cached
     * snapshot to total its bytes: 32 entries of up to 5000 files each is
     * ~160k stat calls per miss, serialized behind the global sweep lock.
     * Entries written before the marker carried a size fall back to a walk.
     */
    private long size(Path path) throws IOException {
        Path marker = path.resolve(".complete");
        if (Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)) {
            String[] lines = Files.readString(marker, StandardCharsets.US_ASCII).split("\n");
            if (lines.length > 1) {
                try {
                    return Long.parseLong(lines[1].strip());
                } catch (NumberFormatException ignored) {
                    // fall through to the walk
                }
            }
        }
        return walkSize(path);
    }

    private long walkSize(Path path) throws IOException {
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

    private static final class ProcessLease {
        private final FileChannel channel;
        private final FileLock lock;
        private int references;

        private ProcessLease(FileChannel channel, FileLock lock, int references) {
            this.channel = channel;
            this.lock = lock;
            this.references = references;
        }
    }

    private record ExclusiveLease(FileChannel channel, FileLock lock) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            lock.close();
            channel.close();
        }
    }
}
