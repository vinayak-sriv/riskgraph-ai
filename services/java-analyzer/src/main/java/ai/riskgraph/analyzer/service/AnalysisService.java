package ai.riskgraph.analyzer.service;

import static ai.riskgraph.analyzer.model.AnalysisModels.AnalysisResponse;
import static ai.riskgraph.analyzer.model.AnalysisModels.ChangedFile;
import static ai.riskgraph.analyzer.model.AnalysisModels.Diagnostic;
import static ai.riskgraph.analyzer.model.AnalysisModels.ExtractionCoverage;
import static ai.riskgraph.analyzer.model.AnalysisModels.RepositoryProvenance;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PreDestroy;
import tools.jackson.databind.ObjectMapper;

import ai.riskgraph.analyzer.extract.SpringEndpointExtractor;
import ai.riskgraph.analyzer.extract.SpringEndpointExtractor.ExtractionResult;
import ai.riskgraph.analyzer.service.DeterministicExtractionCache.CachedExtraction;
import ai.riskgraph.analyzer.source.GitSourceAcquirer;

@Service
public class AnalysisService {
    private static final Logger LOG = LoggerFactory.getLogger(AnalysisService.class);
    public static final String SCHEMA_VERSION = "1.1.0";
    public static final String ANALYZER_VERSION = "0.4.1";

    private final GitSourceAcquirer sourceAcquirer;
    private final SpringEndpointExtractor extractor;
    private final Clock clock;
    private final int maxAnalysisSeconds;
    private final ExecutorService executor;
    private final boolean processIsolation;
    private final String executableJar;
    private final ObjectMapper mapper;
    private final DeterministicExtractionCache extractionCache;
    private final Map<String, AnalysisResponse> isolatedResponses;
    private final FrameworkPreflightChecker frameworkPreflight = new FrameworkPreflightChecker();

    @Autowired
    public AnalysisService(
            GitSourceAcquirer sourceAcquirer,
            SpringEndpointExtractor extractor,
            @Value("${riskgraph.analyzer.max-analysis-seconds:60}") int maxAnalysisSeconds,
            @Value("${riskgraph.analyzer.max-concurrent-analyses:2}") int maxConcurrentAnalyses,
            @Value("${riskgraph.analyzer.max-queued-analyses:4}") int maxQueuedAnalyses,
            @Value("${riskgraph.analyzer.process-isolation:false}") boolean processIsolation,
            @Value("${riskgraph.analyzer.executable-jar:}") String executableJar,
            @Value("${riskgraph.analyzer.extraction-cache-max-entries:64}") int extractionCacheMaxEntries,
            ObjectMapper mapper
    ) {
        this(sourceAcquirer, extractor, Clock.systemUTC(), maxAnalysisSeconds,
                maxConcurrentAnalyses, maxQueuedAnalyses, processIsolation, executableJar, mapper,
                extractionCacheMaxEntries);
    }

    public AnalysisService(GitSourceAcquirer sourceAcquirer, SpringEndpointExtractor extractor) {
        this(sourceAcquirer, extractor, Clock.systemUTC(), 60, 2, 4, false, "", null);
    }

    AnalysisService(
            GitSourceAcquirer sourceAcquirer,
            SpringEndpointExtractor extractor,
            Clock clock,
            int maxAnalysisSeconds
    ) {
        this(sourceAcquirer, extractor, clock, maxAnalysisSeconds, 2, 4, false, "", null);
    }

    AnalysisService(
            GitSourceAcquirer sourceAcquirer,
            SpringEndpointExtractor extractor,
            Clock clock,
            int maxAnalysisSeconds,
            int maxConcurrentAnalyses,
            int maxQueuedAnalyses
    ) {
        this(sourceAcquirer, extractor, clock, maxAnalysisSeconds, maxConcurrentAnalyses,
                maxQueuedAnalyses, false, "", null);
    }

    AnalysisService(
            GitSourceAcquirer sourceAcquirer,
            SpringEndpointExtractor extractor,
            Clock clock,
            int maxAnalysisSeconds,
            int maxConcurrentAnalyses,
            int maxQueuedAnalyses,
            boolean processIsolation,
            String executableJar,
            ObjectMapper mapper
    ) {
        this(sourceAcquirer, extractor, clock, maxAnalysisSeconds, maxConcurrentAnalyses,
                maxQueuedAnalyses, processIsolation, executableJar, mapper, 64);
    }

    AnalysisService(
            GitSourceAcquirer sourceAcquirer,
            SpringEndpointExtractor extractor,
            Clock clock,
            int maxAnalysisSeconds,
            int maxConcurrentAnalyses,
            int maxQueuedAnalyses,
            boolean processIsolation,
            String executableJar,
            ObjectMapper mapper,
            int extractionCacheMaxEntries
    ) {
        this.sourceAcquirer = sourceAcquirer;
        this.extractor = extractor;
        this.clock = clock;
        this.maxAnalysisSeconds = maxAnalysisSeconds;
        this.processIsolation = processIsolation;
        this.executableJar = executableJar;
        this.mapper = mapper;
        this.extractionCache = new DeterministicExtractionCache(extractionCacheMaxEntries);
        int isolatedCacheCapacity = Math.max(1, extractionCacheMaxEntries);
        this.isolatedResponses = java.util.Collections.synchronizedMap(
                new LinkedHashMap<>(16, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, AnalysisResponse> eldest) {
                        return size() > isolatedCacheCapacity;
                    }
                });
        int workers = Math.max(1, maxConcurrentAnalyses);
        int queueSize = Math.max(1, maxQueuedAnalyses);
        this.executor = new ThreadPoolExecutor(workers, workers, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueSize),
                Thread.ofPlatform().daemon(true).name("riskgraph-analysis-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    public AnalysisResponse analyze(Path repositoryPath, String oldCommit, String newCommit) {
        Future<AnalysisResponse> future;
        try {
            future = executor.submit(() -> processIsolation
                    ? analyzeIsolated(repositoryPath, oldCommit, newCommit)
                    : analyzeInternal(repositoryPath, oldCommit, newCommit));
        } catch (RejectedExecutionException error) {
            throw new AnalysisException("ANALYZER_BUSY", "Analyzer capacity is exhausted; retry later");
        }
        try {
            return future.get(maxAnalysisSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException error) {
            future.cancel(true);
            throw new AnalysisException("ANALYSIS_TIMEOUT", "Analysis exceeded the configured time limit");
        } catch (InterruptedException error) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new AnalysisException("ANALYSIS_INTERRUPTED", "Analysis was interrupted");
        } catch (ExecutionException error) {
            if (error.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new AnalysisException("ANALYSIS_FAILED", "Analysis failed unexpectedly");
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    public AnalysisResponse analyzeDirect(Path repositoryPath, String oldCommit, String newCommit) {
        return analyzeInternal(repositoryPath, oldCommit, newCommit);
    }

    /**
     * Isolated analysis with a parent-side result cache.
     *
     * <p>The worker process exits as soon as it has written its result, taking
     * its own {@link DeterministicExtractionCache} with it, so under process
     * isolation that cache can never record a hit. Caching the worker's
     * response in the long-lived parent is what makes repeat analyses cheap.
     * Only {@code analyzed_at} is re-stamped, so a hit and a miss are
     * otherwise byte-identical.
     */
    private AnalysisResponse analyzeIsolated(Path repositoryPath, String oldCommit, String newCommit) {
        String cacheKey = analysisId(
                sourceAcquirer.repositoryIdentity(repositoryPath),
                oldCommit.toLowerCase(Locale.ROOT),
                newCommit.toLowerCase(Locale.ROOT),
                extractor.configurationFingerprint());
        AnalysisResponse cached = isolatedResponses.get(cacheKey);
        if (cached != null) {
            LOG.info("extraction_cache_hit analysis_id={}", cached.analysis_id());
            return restamped(cached);
        }
        LOG.info("extraction_cache_miss analysis_id={}", cacheKey);
        AnalysisResponse response = analyzeInWorkerProcess(repositoryPath, oldCommit, newCommit);
        isolatedResponses.put(cacheKey, response);
        return response;
    }

    private AnalysisResponse restamped(AnalysisResponse response) {
        return new AnalysisResponse(
                response.schema_version(), response.analyzer_version(),
                response.analyzer_config_hash(), response.analysis_id(), clock.instant(),
                response.provenance(), response.changed_files(), response.before(),
                response.after(), response.coverage(), response.diagnostics());
    }

    private AnalysisResponse analyzeInWorkerProcess(Path repositoryPath, String oldCommit, String newCommit) {
        if (executableJar.isBlank() || !Files.isRegularFile(Path.of(executableJar))) {
            throw new AnalysisException("ANALYZER_WORKER_UNAVAILABLE",
                    "Process isolation is enabled but the analyzer executable is unavailable");
        }
        Path output = null;
        Path log = null;
        Process process = null;
        try {
            output = Files.createTempFile("riskgraph-analysis-", ".json");
            log = Files.createTempFile("riskgraph-analysis-", ".log");
            String javaBinary = Path.of(System.getProperty("java.home"), "bin",
                    System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java").toString();
            // The worker is short-lived and shares the parent's cgroup: cap its
            // heap and skip the C2 warm-up it will never amortize, or N workers
            // each size themselves to 25% of the whole container limit.
            process = new ProcessBuilder(javaBinary,
                    "-XX:MaxRAMPercentage=25", "-XX:TieredStopAtLevel=1", "-Xss512k",
                    "-XX:+ExitOnOutOfMemoryError",
                    "-jar", executableJar, "--riskgraph-worker",
                    repositoryPath.toString(), oldCommit, newCommit, output.toString())
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            int exitCode = process.waitFor();
            if (exitCode != 0 || Files.size(output) == 0 || Files.size(output) > 32L * 1024 * 1024) {
                throw new AnalysisException("ANALYZER_WORKER_FAILED",
                        "Isolated analyzer worker failed or returned an invalid result");
            }
            return mapper.readValue(output.toFile(), AnalysisResponse.class);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AnalysisException("ANALYSIS_INTERRUPTED", "Isolated analysis was interrupted");
        } catch (IOException | RuntimeException error) {
            if (error instanceof AnalysisException analysisException) throw analysisException;
            throw new AnalysisException("ANALYZER_WORKER_FAILED", "Unable to run isolated analyzer worker");
        } finally {
            if (process != null && process.isAlive()) {
                process.descendants().forEach(handle -> handle.destroyForcibly());
                process.destroyForcibly();
            }
            cleanupWorkerArtifact(output, "output");
            cleanupWorkerArtifact(log, "log");
        }
    }

    private void cleanupWorkerArtifact(Path artifact, String kind) {
        if (artifact == null) return;
        try {
            Files.deleteIfExists(artifact);
        } catch (IOException error) {
            LOG.warn("worker_temp_cleanup_failed artifact_kind={} artifact={} error_type={}",
                    kind, artifact.getFileName(), error.getClass().getSimpleName());
        }
    }

    private AnalysisResponse analyzeInternal(Path repositoryPath, String oldCommit, String newCommit) {
        try (var acquired = sourceAcquirer.acquire(repositoryPath, oldCommit, newCommit)) {
            String configHash = extractor.configurationFingerprint();
            String analysisId = analysisId(
                    acquired.repositoryIdentity(), acquired.oldCommit(), acquired.newCommit(), configHash);
            LOG.info("analysis_started analysis_id={} old_commit={} new_commit={}",
                    analysisId, acquired.oldCommit(), acquired.newCommit());
            var cached = extractionCache.get(analysisId);
            if (cached.isPresent()) {
                LOG.info("extraction_cache_hit analysis_id={}", analysisId);
                return response(acquired, configHash, analysisId, cached.orElseThrow());
            }
            LOG.info("extraction_cache_miss analysis_id={}", analysisId);
            ExtractionResult before = extractor.extract(
                    acquired.oldSnapshot(), changedRanges(acquired.changedFiles(), true));
            ExtractionResult after = extractor.extract(
                    acquired.newSnapshot(), changedRanges(acquired.changedFiles(), false));
            List<Diagnostic> diagnostics = new ArrayList<>(before.diagnostics());
            diagnostics.addAll(after.diagnostics());
            if (acquired.changedFiles().isEmpty()) {
                boolean noJavaAnywhere = !frameworkPreflight.hasJavaSource(acquired.oldSnapshot())
                        && !frameworkPreflight.hasJavaSource(acquired.newSnapshot());
                if (noJavaAnywhere) {
                    diagnostics.add(new Diagnostic("ERROR", "UNSUPPORTED_FRAMEWORK",
                            "No Java source was found in either revision and no Java files changed "
                                    + "between the commits; RiskGraph AI only analyzes Java 21 / Spring Boot "
                                    + "repositories with annotation-based authorization",
                            null));
                } else {
                    diagnostics.add(new Diagnostic("INFO", "NO_JAVA_CHANGES",
                            "No changed Java files were found between the commits", null));
                }
            }
            CachedExtraction extraction = new CachedExtraction(
                    before.endpoints(), after.endpoints(), combine(before.coverage(), after.coverage()), diagnostics)
                    .immutableCopy();
            if (extractionCache.put(analysisId, extraction)) {
                LOG.info("extraction_cache_evicted analysis_id={}", analysisId);
            }
            AnalysisResponse response = response(acquired, configHash, analysisId, extraction);
            LOG.info("analysis_completed analysis_id={} changed_files={} endpoints={} diagnostics={}",
                    analysisId, acquired.changedFiles().size(), response.coverage().endpoints_emitted(),
                    response.diagnostics().size());
            return response;
        }
    }

    private AnalysisResponse response(
            GitSourceAcquirer.AcquiredRevisions acquired,
            String configHash,
            String analysisId,
            CachedExtraction extraction
    ) {
        return new AnalysisResponse(
                    SCHEMA_VERSION,
                    ANALYZER_VERSION,
                    configHash,
                    analysisId,
                    clock.instant(),
                    new RepositoryProvenance(acquired.repositoryPath().toString(), acquired.repositoryIdentity(),
                            acquired.oldCommit(), acquired.newCommit()),
                    acquired.changedFiles(),
                    extraction.before(),
                    extraction.after(),
                    extraction.coverage(),
                    extraction.diagnostics());
    }

    DeterministicExtractionCache.CacheStats extractionCacheStats() {
        return extractionCache.stats();
    }

    private Map<String, List<ai.riskgraph.analyzer.model.AnalysisModels.ChangedRange>> changedRanges(
            List<ChangedFile> files, boolean oldRevision) {
        Map<String, List<ai.riskgraph.analyzer.model.AnalysisModels.ChangedRange>> ranges = new LinkedHashMap<>();
        for (ChangedFile file : files) {
            String path = oldRevision ? file.old_path() : file.new_path();
            if (path != null) {
                ranges.put(path, oldRevision ? file.old_ranges() : file.new_ranges());
            }
        }
        return ranges;
    }

    private String analysisId(String repositoryIdentity, String oldCommit, String newCommit, String configHash) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = repositoryIdentity + "\n" + oldCommit + "\n" + newCommit + "\n"
                    + ANALYZER_VERSION + "\n" + configHash;
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    static ExtractionCoverage combine(ExtractionCoverage before, ExtractionCoverage after) {
        int endpointCount = before.endpoints_emitted() + after.endpoints_emitted();
        int withService = before.endpoints_with_service() + after.endpoints_with_service();
        int withRepository = before.endpoints_with_repository() + after.endpoints_with_repository();
        // A failed snapshot carries zero coverage even when it emitted no rows.
        // Do not turn parser failure into perfect coverage during aggregation.
        boolean failedSnapshot = (before.endpoints_emitted() == 0 && before.coverage_ratio() == 0.0)
                || (after.endpoints_emitted() == 0 && after.coverage_ratio() == 0.0);
        double coverage = failedSnapshot ? 0.0
                : endpointCount == 0 ? 1.0 : (withService + withRepository) / (2.0 * endpointCount);
        return new ExtractionCoverage(
                Math.max(before.java_files_considered(), after.java_files_considered()),
                Math.max(before.controllers_discovered(), after.controllers_discovered()),
                endpointCount, withService, withRepository, Math.round(coverage * 1000.0) / 1000.0);
    }
}
