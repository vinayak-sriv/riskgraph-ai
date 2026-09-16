package ai.riskgraph.analyzer.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.riskgraph.analyzer.extract.SpringEndpointExtractor;
import ai.riskgraph.analyzer.extract.SensitivityPolicy;
import ai.riskgraph.analyzer.source.GitSourceAcquirer;
import ai.riskgraph.analyzer.source.SnapshotCache;

class AnalysisServiceIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(AnalysisServiceIntegrationTest.class);
    @TempDir
    Path tempDir;

    @Test
    void extractsAuthorizationRemovalAndRepositoryPathFromRealCommits() throws Exception {
        CommitPair pair = createAuthorizationRemovalRepository();
        AnalysisService service = new AnalysisService(
                new GitSourceAcquirer(tempDir.toString()),
                new SpringEndpointExtractor(new SensitivityPolicy("")));

        var result = service.analyze(pair.repository(), pair.oldCommit(), pair.newCommit());

        assertThat(result.schema_version()).isEqualTo("1.1.0");
        assertThat(result.analysis_id()).matches("[0-9a-f]{64}");
        assertThat(result.provenance().old_commit()).isEqualTo(pair.oldCommit());
        assertThat(result.changed_files()).singleElement().satisfies(file -> {
            assertThat(file.status()).isEqualTo("MODIFY");
            assertThat(file.new_path()).endsWith("AdminController.java");
            assertThat(file.old_ranges()).isNotEmpty();
            assertThat(file.new_ranges()).isNotEmpty();
        });
        assertThat(result.before()).singleElement().satisfies(evidence -> {
            assertThat(evidence.endpoint().endpoint()).isEqualTo("/admin/export");
            assertThat(evidence.endpoint().method()).isEqualTo("GET");
            assertThat(evidence.endpoint().authentication()).isTrue();
            assertThat(evidence.endpoint().required_role()).isEqualTo("ADMIN");
            assertThat(evidence.endpoint().service()).isEqualTo("CustomerService");
            assertThat(evidence.endpoint().repository()).isEqualTo("CustomerRepository");
            assertThat(evidence.endpoint().resource()).isEqualTo("Customer");
            assertThat(evidence.endpoint().sensitivity()).isEqualTo("HIGH");
            assertThat(evidence.source_location().path()).endsWith("AdminController.java");
            assertThat(evidence.source_location().start_line()).isPositive();
            assertThat(evidence.qualified_controller()).isEqualTo("demo.AdminController");
            assertThat(evidence.method_signature()).isEqualTo("export()");
            assertThat(evidence.dependency_paths()).hasSize(1);
            assertThat(evidence.sensitivity_evidence().matched_rule()).isEqualTo("Customer@1.1.0");
            assertThat(evidence.extraction_confidence().overall()).isEqualTo("HIGH");
        });
        assertThat(result.after()).singleElement().satisfies(evidence -> {
            assertThat(evidence.endpoint().authentication()).isFalse();
            assertThat(evidence.endpoint().required_role()).isNull();
        });
        assertThat(result.coverage().coverage_ratio()).isEqualTo(1.0);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void repeatedAnalysisHasStableEvidenceAndDiffs() throws Exception {
        CommitPair pair = createAuthorizationRemovalRepository();
        Path cacheRoot = tempDir.resolve("snapshot-cache");
        AnalysisService service = new AnalysisService(
                new GitSourceAcquirer(tempDir.toString(), 5000, 2L * 1024 * 1024,
                        50L * 1024 * 1024,
                        new SnapshotCache(cacheRoot.toString(), 8, 100L * 1024 * 1024, 1)),
                new SpringEndpointExtractor(new SensitivityPolicy("")));

        long coldStarted = System.nanoTime();
        var first = service.analyze(pair.repository(), pair.oldCommit(), pair.newCommit());
        long coldMillis = (System.nanoTime() - coldStarted) / 1_000_000;
        long warmStarted = System.nanoTime();
        var second = service.analyze(pair.repository(), pair.oldCommit(), pair.newCommit());
        long warmMillis = (System.nanoTime() - warmStarted) / 1_000_000;
        long snapshotBytes;
        try (var paths = Files.walk(cacheRoot)) {
            snapshotBytes = paths.filter(Files::isRegularFile).mapToLong(path -> {
                try {
                    return Files.size(path);
                } catch (Exception error) {
                    return 0;
                }
            }).sum();
        }
        LOG.info("cache_benchmark fixture=annotation-removal cold_ms={} warm_ms={} snapshot_bytes={} "
                        + "fallback_count=0 extraction_hits=1 extraction_misses=1",
                coldMillis, warmMillis, snapshotBytes);

        assertThat(second.provenance()).isEqualTo(first.provenance());
        assertThat(second.analysis_id()).isEqualTo(first.analysis_id());
        assertThat(second.changed_files()).isEqualTo(first.changed_files());
        assertThat(second.before()).isEqualTo(first.before());
        assertThat(second.after()).isEqualTo(first.after());
        assertThat(second.coverage()).isEqualTo(first.coverage());
        assertThat(second.diagnostics()).isEqualTo(first.diagnostics());
        assertThat(second.analyzed_at()).isAfterOrEqualTo(first.analyzed_at());
        assertThat(service.extractionCacheStats().hits()).isEqualTo(1);
        assertThat(service.extractionCacheStats().misses()).isEqualTo(1);
    }

    private CommitPair createAuthorizationRemovalRepository() throws Exception {
        Path repository = Files.createDirectory(tempDir.resolve("repo-" + System.nanoTime()));
        write(repository, "src/main/java/demo/CustomerRepository.java", """
                package demo;
                interface CustomerRepository { Object findAll(); }
                """);
        write(repository, "src/main/java/demo/CustomerService.java", """
                package demo;
                class CustomerService {
                    private CustomerRepository customerRepository;
                    Object exportCustomers() { return customerRepository.findAll(); }
                }
                """);
        String protectedController = """
                package demo;
                @RestController
                @RequestMapping("/admin")
                class AdminController {
                    private CustomerService customerService;
                    @GetMapping("/export")
                    @PreAuthorize("hasRole('ADMIN')")
                    Object export() { return customerService.exportCustomers(); }
                }
                """;
        String publicController = protectedController.replace(
                "    @PreAuthorize(\"hasRole('ADMIN')\")\n", "");

        String oldCommit;
        String newCommit;
        try (Git git = Git.init().setDirectory(repository.toFile()).call()) {
            write(repository, "src/main/java/demo/AdminController.java", protectedController);
            git.add().addFilepattern(".").call();
            oldCommit = git.commit().setMessage("protected endpoint")
                    .setAuthor("RiskGraph", "test@riskgraph.ai").call().getName();
            write(repository, "src/main/java/demo/AdminController.java", publicController);
            git.add().addFilepattern(".").call();
            newCommit = git.commit().setMessage("remove authorization")
                    .setAuthor("RiskGraph", "test@riskgraph.ai").call().getName();
        }
        return new CommitPair(repository, oldCommit, newCommit);
    }

    private void write(Path repository, String relativePath, String contents) throws Exception {
        Path file = repository.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, contents);
    }

    private record CommitPair(Path repository, String oldCommit, String newCommit) {
    }
}
