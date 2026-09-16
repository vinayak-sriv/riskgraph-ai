package ai.riskgraph.analyzer.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TempArtifactSweeper {
    private static final Logger LOG = LoggerFactory.getLogger(TempArtifactSweeper.class);
    private static final List<String> SAFE_PREFIXES = List.of(
            "riskgraph-old-", "riskgraph-new-", "riskgraph-analysis-");
    private final Path tempRoot;
    private final Clock clock;
    private final Duration minimumAge;
    private final int maxCandidates;

    @Autowired
    public TempArtifactSweeper(
            @Value("${riskgraph.analyzer.temp-cleanup-minimum-age-hours:24}")
            long minimumAgeHours,
            @Value("${riskgraph.analyzer.temp-cleanup-max-candidates:100}") int maxCandidates) {
        this(Path.of(System.getProperty("java.io.tmpdir")), Clock.systemUTC(),
                Duration.ofHours(Math.max(1, minimumAgeHours)), maxCandidates);
    }

    TempArtifactSweeper(Path tempRoot, Clock clock, Duration minimumAge, int maxCandidates) {
        this.tempRoot = tempRoot.toAbsolutePath().normalize();
        this.clock = clock;
        this.minimumAge = minimumAge;
        this.maxCandidates = Math.max(1, maxCandidates);
    }

    @Scheduled(fixedDelayString = "${riskgraph.analyzer.temp-cleanup-delay-ms:3600000}",
            initialDelayString = "${riskgraph.analyzer.temp-cleanup-initial-delay-ms:60000}")
    public void scheduledSweep() {
        SweepResult result = sweep();
        LOG.info("temp_sweep_completed examined={} removed={} failed={}",
                result.examined(), result.removed(), result.failed());
    }

    SweepResult sweep() {
        int examined = 0;
        int removed = 0;
        int failed = 0;
        Instant cutoff = clock.instant().minus(minimumAge);
        try (var candidates = Files.list(tempRoot)) {
            for (Path candidate : candidates.filter(this::isSafeCandidate)
                    .limit(maxCandidates).toList()) {
                examined++;
                try {
                    if (Files.getLastModifiedTime(candidate, LinkOption.NOFOLLOW_LINKS)
                            .toInstant().isAfter(cutoff)) continue;
                    deleteTree(candidate);
                    removed++;
                } catch (IOException error) {
                    failed++;
                    LOG.warn("temp_cleanup_failed artifact={} error_type={}",
                            candidate.getFileName(), error.getClass().getSimpleName());
                }
            }
        } catch (IOException error) {
            LOG.warn("temp_sweep_failed error_type={}", error.getClass().getSimpleName());
            failed++;
        }
        return new SweepResult(examined, removed, failed);
    }

    private boolean isSafeCandidate(Path candidate) {
        Path normalized = candidate.toAbsolutePath().normalize();
        if (!tempRoot.equals(normalized.getParent())) return false;
        String name = normalized.getFileName().toString();
        return SAFE_PREFIXES.stream().anyMatch(name::startsWith);
    }

    private void deleteTree(Path root) throws IOException {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            Files.deleteIfExists(root);
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    record SweepResult(int examined, int removed, int failed) { }
}
