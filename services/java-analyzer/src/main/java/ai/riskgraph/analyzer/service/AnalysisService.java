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
import ai.riskgraph.analyzer.source.GitSourceAcquirer;

@Service
public class AnalysisService {
    private static final Logger LOG = LoggerFactory.getLogger(AnalysisService.class);
    public static final String SCHEMA_VERSION = "1.1.0";
    public static final String ANALYZER_VERSION = "0.4.0";

    private final GitSourceAcquirer sourceAcquirer;
    private final SpringEndpointExtractor extractor;
    private final Clock clock;
    private final int maxAnalysisSeconds;
    private final ExecutorService executor;
    private final boolean processIsolation;
    private final String executableJar;
    private final ObjectMapper mapper;

    @Autowired
    public AnalysisService(
            GitSourceAcquirer sourceAcquirer,
            SpringEndpointExtractor extractor,
            @Value("${riskgraph.analyzer.max-analysis-seconds:60}") int maxAnalysisSeconds,
            @Value("${riskgraph.analyzer.max-concurrent-analyses:2}") int maxConcurrentAnalyses,
            @Value("${riskgraph.analyzer.max-queued-analyses:4}") int maxQueuedAnalyses,
            @Value("${riskgraph.analyzer.process-isolation:false}") boolean processIsolation,
            @Value("${riskgraph.analyzer.executable-jar:}") String executableJar,
            ObjectMapper mapper
    ) {
        this(sourceAcquirer, extractor, Clock.systemUTC(), maxAnalysisSeconds,
                maxConcurrentAnalyses, maxQueuedAnalyses, processIsolation, executableJar, mapper);
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
        this.sourceAcquirer = sourceAcquirer;
        this.extractor = extractor;
        this.clock = clock;
        this.maxAnalysisSeconds = maxAnalysisSeconds;
        this.processIsolation = processIsolation;
        this.executableJar = executableJar;
        this.mapper = mapper;
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
                    ? analyzeInWorkerProcess(repositoryPath, oldCommit, newCommit)
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
            process = new ProcessBuilder(javaBinary, "-jar", executableJar, "--riskgraph-worker",
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
            try { if (output != null) Files.deleteIfExists(output); } catch (IOException ignored) { }
            try { if (log != null) Files.deleteIfExists(log); } catch (IOException ignored) { }
        }
    }

    private AnalysisResponse analyzeInternal(Path repositoryPath, String oldCommit, String newCommit) {
        try (var acquired = sourceAcquirer.acquire(repositoryPath, oldCommit, newCommit)) {
            String configHash = extractor.configurationFingerprint();
            String analysisId = analysisId(
                    acquired.repositoryIdentity(), acquired.oldCommit(), acquired.newCommit(), configHash);
            LOG.info("analysis_started analysis_id={} old_commit={} new_commit={}",
                    analysisId, acquired.oldCommit(), acquired.newCommit());
            ExtractionResult before = extractor.extract(
                    acquired.oldSnapshot(), changedRanges(acquired.changedFiles(), true));
            ExtractionResult after = extractor.extract(
                    acquired.newSnapshot(), changedRanges(acquired.changedFiles(), false));
            List<Diagnostic> diagnostics = new ArrayList<>(before.diagnostics());
            diagnostics.addAll(after.diagnostics());
            if (acquired.changedFiles().isEmpty()) {
                diagnostics.add(new Diagnostic("INFO", "NO_JAVA_CHANGES",
                        "No changed Java files were found between the commits", null));
            }
            AnalysisResponse response = new AnalysisResponse(
                    SCHEMA_VERSION,
                    ANALYZER_VERSION,
                    configHash,
                    analysisId,
                    clock.instant(),
                    new RepositoryProvenance(acquired.repositoryPath().toString(), acquired.repositoryIdentity(),
                            acquired.oldCommit(), acquired.newCommit()),
                    acquired.changedFiles(),
                    before.endpoints(),
                    after.endpoints(),
                    combine(before.coverage(), after.coverage()),
                    List.copyOf(diagnostics));
            LOG.info("analysis_completed analysis_id={} changed_files={} endpoints={} diagnostics={}",
                    analysisId, acquired.changedFiles().size(), response.coverage().endpoints_emitted(), diagnostics.size());
            return response;
        }
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
