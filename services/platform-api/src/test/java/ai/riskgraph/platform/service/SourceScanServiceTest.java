package ai.riskgraph.platform.service;

import ai.riskgraph.platform.client.AnalysisClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SourceScanServiceTest {
    @TempDir Path root;
    final JsonMapper mapper = JsonMapper.builder().build();

    @Test void validatesContractAndProvenanceBeforeSendingCanonicalIr() throws Exception {
        var client = mock(AnalysisClient.class);
        ObjectNode envelope = (ObjectNode) mapper.readTree(getClass().getResourceAsStream(
            "/contracts/ir/examples/analysis-envelope.json"));
        ((ObjectNode) envelope.path("provenance")).put("repository_path", root.toRealPath().toString());
        String oldSha = envelope.at("/provenance/old_commit").asString();
        String newSha = envelope.at("/provenance/new_commit").asString();
        when(client.post(eq("analyzer"), eq("/analyze"), any())).thenReturn(envelope);
        when(client.post(eq("graph"), eq("/analysis"), any())).thenAnswer(invocation -> {
            JsonNode request = invocation.getArgument(2);
            assertThat(request.at("/before/0").has("source_location")).isFalse();
            assertThat(request.at("/after/0/endpoint").isString()).isTrue();
            throw new PipelineException("DEPENDENCY_UNAVAILABLE", 503, "test");
        });
        var service = new SourceScanService(client, new ContractValidator(), mapper, "analyzer", "graph", root.toString(), new MemoryScanStore());
        assertThatThrownBy(() -> service.analyze(root.toString(), oldSha, newSha))
            .isInstanceOf(PipelineException.class).hasMessage("test");
        ((ObjectNode) envelope.path("provenance")).put("new_commit", "f".repeat(40));
        assertThatThrownBy(() -> service.analyze(root.toString(), oldSha, newSha))
            .hasMessage("Analyzer provenance does not match request");
    }

    @Test void routesAPositivelyDetectedFastApiRepositoryToThePythonAnalyzer() throws Exception {
        java.nio.file.Files.writeString(root.resolve("main.py"), "from fastapi import FastAPI");
        var client = mock(AnalysisClient.class);
        ObjectNode envelope = (ObjectNode) mapper.readTree(getClass().getResourceAsStream(
            "/contracts/ir/examples/analysis-envelope.json"));
        ((ObjectNode) envelope.path("provenance")).put("repository_path", root.toRealPath().toString());
        String oldSha = envelope.at("/provenance/old_commit").asString();
        String newSha = envelope.at("/provenance/new_commit").asString();
        when(client.post(eq("python"), eq("/analyze"), any())).thenReturn(envelope);
        when(client.post(eq("graph"), eq("/analysis"), any())).thenThrow(
            new PipelineException("DEPENDENCY_UNAVAILABLE", 503, "reached the graph step"));
        var service = new SourceScanService(client, new ContractValidator(), mapper,
            "analyzer", "graph", "python", root.toString(), new MemoryScanStore(),
            new FindingEnrichmentService(client, new ContractValidator(), mapper),
            new SourceValidationService(client, new ContractValidator(), mapper));
        assertThatThrownBy(() -> service.analyze(root.toString(), oldSha, newSha))
            .hasMessage("reached the graph step");
        verify(client, never()).post(eq("analyzer"), any(), any());
    }

    @Test void aPythonRepositoryFailsClearlyWhenNoPythonAnalyzerIsConfigured() throws Exception {
        java.nio.file.Files.writeString(root.resolve("main.py"), "from fastapi import FastAPI");
        var client = mock(AnalysisClient.class);
        var service = new SourceScanService(client, new ContractValidator(), mapper,
            "analyzer", "graph", root.toString(), new MemoryScanStore());
        assertThatThrownBy(() -> service.analyze(root.toString(), "a".repeat(40), "b".repeat(40)))
            .hasMessageContaining("Python analyzer is not configured");
        verifyNoInteractions(client);
    }

    @Test void explicitJavaSelectionSupportsTrustedSelfAnalysisOfAMixedRepository() throws Exception {
        java.nio.file.Files.writeString(root.resolve("App.java"), "class App {}\n");
        java.nio.file.Files.writeString(root.resolve("main.py"), "from fastapi import FastAPI\n");
        runGit("init");
        runGit("config", "user.email", "test@riskgraph.local");
        runGit("config", "user.name", "RiskGraph Test");
        runGit("add", "App.java", "main.py");
        runGit("commit", "-m", "mixed fixture");
        String commit = runGit("rev-parse", "HEAD").trim();
        var client = mock(AnalysisClient.class);
        ObjectNode envelope = envelopeForRoot();
        ((ObjectNode) envelope.path("provenance")).put("old_commit", commit).put("new_commit", commit);
        when(client.post(eq("analyzer"), eq("/analyze"), any())).thenReturn(envelope);
        when(client.post(eq("graph"), eq("/analysis"), any())).thenThrow(
                new PipelineException("DEPENDENCY_UNAVAILABLE", 503, "reached the graph step"));
        var service = new SourceScanService(client, new ContractValidator(), mapper,
                "analyzer", "graph", "python", root.toString(), new MemoryScanStore(),
                new FindingEnrichmentService(client, new ContractValidator(), mapper),
                new SourceValidationService(client, new ContractValidator(), mapper));
        ReflectionTestUtils.setField(service, "configuredFramework", "JAVA_SPRING");

        assertThatThrownBy(() -> service.analyze(root.toString(), commit, commit))
                .hasMessage("reached the graph step");
        verify(client, never()).post(eq("python"), any(), any());
    }

    @Test void unsupportedCommittedFrameworkProducesNoVerdictOrAnalyzerCall() throws Exception {
        java.nio.file.Files.writeString(root.resolve("script.py"), "print('hello')\n");
        runGit("init");
        runGit("config", "user.email", "test@riskgraph.local");
        runGit("config", "user.name", "RiskGraph Test");
        runGit("add", "script.py");
        runGit("commit", "-m", "plain python");
        String commit = runGit("rev-parse", "HEAD").trim();
        var client = mock(AnalysisClient.class);
        var service = new SourceScanService(client, new ContractValidator(), mapper,
                "analyzer", "graph", "python", root.toString(), new MemoryScanStore(),
                new FindingEnrichmentService(client, new ContractValidator(), mapper),
                new SourceValidationService(client, new ContractValidator(), mapper));

        PipelineException failure = catchThrowableOfType(PipelineException.class,
                () -> service.analyze(root.toString(), commit, commit));

        assertThat(failure.code).isEqualTo("UNSUPPORTED_FRAMEWORK");
        verifyNoInteractions(client);
    }

    @Test void rejectsMalformedEnvelopeAndDisallowedPaths() {
        var client = mock(AnalysisClient.class);
        when(client.post(any(), any(), any())).thenReturn(mapper.createObjectNode());
        var service = new SourceScanService(client, new ContractValidator(), mapper, "analyzer", "graph", root.toString(), new MemoryScanStore());
        assertThatThrownBy(() -> service.analyze(root.toString(), "a".repeat(40), "b".repeat(40)))
            .hasMessageContaining("does not match");
        assertThatThrownBy(() -> service.analyze(root.getParent().toString(), "a".repeat(40), "b".repeat(40)))
            .hasMessageContaining("outside the allowlist");
        assertThatThrownBy(() -> service.get("missing")).hasMessage("Scan does not exist");
    }

    @Test void identicalReanalysisRetainsExistingValidationEvidence() throws Exception {
        var client=mock(AnalysisClient.class);
        ObjectNode envelope=(ObjectNode) mapper.readTree(getClass().getResourceAsStream("/contracts/ir/examples/analysis-envelope.json"));
        ((ObjectNode) envelope.path("provenance")).put("repository_path",root.toRealPath().toString());
        String oldSha=envelope.at("/provenance/old_commit").asString(), newSha=envelope.at("/provenance/new_commit").asString();
        when(client.post(eq("analyzer"),eq("/analyze"),any())).thenReturn(envelope);
        ObjectNode graph=mapper.createObjectNode().put("verdict","BLOCK");
        graph.putObject("graph_delta").putArray("new_paths");
        when(client.post(eq("graph"),eq("/analysis"),any())).thenAnswer(call -> graph.deepCopy());
        var store=new MemoryScanStore();
        var service=new SourceScanService(client,mock(ContractValidator.class),mapper,"analyzer","graph",root.toString(),store);
        ObjectNode first=(ObjectNode) service.analyze(root.toString(),oldSha,newSha);
        first.put("validation_status","CONFIRMED");
        first.putObject("validation").put("status","CONFIRMED").put("source_commit",newSha).put("cleanup_complete",true);
        store.save(first);
        var repeated=service.analyze(root.toString(),oldSha,newSha);
        assertThat(repeated.path("scan_id")).isEqualTo(first.path("scan_id"));
        assertThat(repeated.path("validation")).isEqualTo(first.path("validation"));
        assertThat(repeated.path("validation_status").asString()).isEqualTo("CONFIRMED");
        assertThat(repeated.path("final_verdict").asString()).isEqualTo("BLOCK");
    }

    @Test void parserErrorsAndEmptyChangedSurfacesNeverReportHighConfidence() throws Exception {
        for (boolean parserError : new boolean[]{true, false}) {
            var client = mock(AnalysisClient.class);
            ObjectNode envelope = (ObjectNode) mapper.readTree(getClass().getResourceAsStream(
                "/contracts/ir/examples/analysis-envelope.json"));
            ((ObjectNode) envelope.path("provenance")).put("repository_path", root.toRealPath().toString());
            if (parserError) envelope.putArray("diagnostics").addObject().put("severity", "ERROR")
                .put("code", "SPOON_MODEL_FAILED").put("message", "Duplicate type").putNull("path");
            else { envelope.putArray("before"); envelope.putArray("after"); }
            when(client.post(eq("analyzer"), eq("/analyze"), any())).thenReturn(envelope);
            when(client.post(eq("graph"), eq("/analysis"), any())).thenAnswer(call -> {
                JsonNode payload = call.getArgument(2);
                assertThat(payload.at("/quality/confidence").asString()).isEqualTo("LOW");
                assertThat(payload.at("/quality/incomplete").asBoolean()).isTrue();
                throw new PipelineException("DEPENDENCY_UNAVAILABLE", 503, "checked quality");
            });
            var service = new SourceScanService(client, new ContractValidator(), mapper,
                "analyzer", "graph", root.toString(), new MemoryScanStore());
            assertThatThrownBy(() -> service.analyze(root.toString(),
                envelope.at("/provenance/old_commit").asString(), envelope.at("/provenance/new_commit").asString()))
                .hasMessage("checked quality");
        }
    }

    @Test void multipleDependencyPathsBecomeIndependentCanonicalRows() throws Exception {
        var client = mock(AnalysisClient.class);
        ObjectNode envelope = envelopeForRoot();
        ObjectNode second = ((ObjectNode) envelope.at("/after/0/dependency_paths/0")).deepCopy();
        second.put("service", "AuditService").put("repository", "AuditRepository")
                .put("resource", "Audit").put("sensitivity", "MEDIUM");
        ((tools.jackson.databind.node.ArrayNode) envelope.at("/after/0/dependency_paths")).add(second);
        when(client.post(eq("analyzer"), eq("/analyze"), any())).thenReturn(envelope);
        when(client.post(eq("graph"), eq("/analysis"), any())).thenAnswer(call -> {
            JsonNode payload = call.getArgument(2);
            assertThat(payload.path("after")).hasSize(2);
            assertThat(payload.at("/after/0/resource").asString()).isEqualTo("Customer");
            assertThat(payload.at("/after/1/resource").asString()).isEqualTo("Audit");
            throw new PipelineException("CHECKED", 503, "checked");
        });
        var service = new SourceScanService(client, new ContractValidator(), mapper,
                "analyzer", "graph", root.toString(), new MemoryScanStore());
        assertThatThrownBy(() -> service.analyze(root.toString(),
                envelope.at("/provenance/old_commit").asString(), envelope.at("/provenance/new_commit").asString()))
                .hasMessage("checked");
    }

    @ParameterizedTest
    @CsvSource({"before, 1999", "before, 2000", "after, 1999", "after, 2000"})
    void acceptsCanonicalRowsThroughTheDocumentedPerRevisionLimit(
            String revision, int rowCount) throws Exception {
        var client = mock(AnalysisClient.class);
        ObjectNode envelope = envelopeWithRows(revision, rowCount);
        when(client.post(eq("analyzer"), eq("/analyze"), any())).thenReturn(envelope);
        when(client.post(eq("graph"), eq("/analysis"), any())).thenAnswer(call -> {
            JsonNode payload = call.getArgument(2);
            assertThat(envelope.path(revision)).hasSize(1);
            assertThat(payload.path(revision)).hasSize(rowCount);
            throw new PipelineException("CHECKED", 503, "checked boundary");
        });
        var service = new SourceScanService(client, new ContractValidator(), mapper,
                "analyzer", "graph", root.toString(), new MemoryScanStore());

        assertThatThrownBy(() -> service.analyze(root.toString(),
                envelope.at("/provenance/old_commit").asString(),
                envelope.at("/provenance/new_commit").asString()))
                .isInstanceOf(PipelineException.class).hasMessage("checked boundary");
        verify(client).post(eq("graph"), eq("/analysis"), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"before", "after"})
    void rejectsCanonicalRowsAboveTheLimitBeforeCallingTheGraphService(String revision)
            throws Exception {
        var client = mock(AnalysisClient.class);
        var store = mock(ScanStore.class);
        ObjectNode envelope = envelopeWithRows(revision, 2001);
        when(client.post(eq("analyzer"), eq("/analyze"), any())).thenReturn(envelope);
        var service = new SourceScanService(client, new ContractValidator(), mapper,
                "analyzer", "graph", root.toString(), store);

        PipelineException failure = catchThrowableOfType(PipelineException.class, () -> service.analyze(
                root.toString(), envelope.at("/provenance/old_commit").asString(),
                envelope.at("/provenance/new_commit").asString()));

        assertThat(envelope.path(revision)).hasSize(1);
        assertThat(failure.code).isEqualTo("ANALYSIS_TOO_LARGE");
        assertThat(failure.status).isEqualTo(413);
        assertThat(failure).hasMessage("Canonical graph input exceeds 2000 rows per revision");
        verify(client, never()).post(eq("graph"), eq("/analysis"), any());
        verifyNoInteractions(store);
    }

    @Test void eachFindingReceivesItsOwnAiExplanation() throws Exception {
        var client = mock(AnalysisClient.class);
        ObjectNode envelope = envelopeForRoot();
        when(client.post(eq("analyzer"), eq("/analyze"), any())).thenReturn(envelope);
        when(client.post(eq("graph"), eq("/analysis"), any())).thenReturn(graphWithTwoFindings());
        when(client.post(any(), eq("/ai/analyze"), any())).thenAnswer(call -> {
            JsonNode request = call.getArgument(2);
            String path = request.path("path").asString();
            ObjectNode ai = mapper.createObjectNode().put("status", "AVAILABLE").put("confirmed", false)
                    .put("provider", "test").put("reason_code", "SCHEMA_VALIDATED");
            ai.putObject("analysis").put("finding", "Finding " + path)
                    .set("evidence", request.path("evidence").deepCopy());
            ((ObjectNode) ai.path("analysis")).put("hypothesis", "Unconfirmed " + path)
                    .put("recommended_test", "GET " + path + " without authentication").put("confidence", "HIGH");
            return ai;
        });
        var service = new SourceScanService(client, mock(ContractValidator.class), mapper,
                "analyzer", "graph", root.toString(), new MemoryScanStore());

        JsonNode result = service.analyze(root.toString(), envelope.at("/provenance/old_commit").asString(),
                envelope.at("/provenance/new_commit").asString());

        assertThat(result.path("findings")).hasSize(2);
        assertThat(result.at("/findings/0/finding_id")).isNotEqualTo(result.at("/findings/1/finding_id"));
        assertThat(result.at("/findings/0/ai/analysis/hypothesis").asString()).contains("/one");
        assertThat(result.at("/findings/1/ai/analysis/hypothesis").asString()).contains("/two");
        JsonNode repeated = service.analyze(root.toString(),
                envelope.at("/provenance/old_commit").asString(),
                envelope.at("/provenance/new_commit").asString());
        assertThat(repeated.at("/findings/0/ai")).isEqualTo(result.at("/findings/0/ai"));
        assertThat(repeated.at("/findings/1/ai")).isEqualTo(result.at("/findings/1/ai"));
        verify(client, times(2)).post(any(), eq("/ai/analyze"), any());
    }

    @Test void unrelatedScansCanAnalyzeConcurrently() throws Exception {
        var client = mock(AnalysisClient.class);
        CountDownLatch entered = new CountDownLatch(2);
        when(client.post(eq("analyzer"), eq("/analyze"), any())).thenAnswer(call -> {
            entered.countDown();
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            ObjectNode envelope = envelopeForRoot();
            JsonNode request = call.getArgument(2);
            ((ObjectNode) envelope.path("provenance")).put("old_commit", request.path("old_commit").asString())
                    .put("new_commit", request.path("new_commit").asString());
            return envelope;
        });
        when(client.post(eq("graph"), eq("/analysis"), any())).thenReturn(emptyGraphResult());
        var service = new SourceScanService(client, mock(ContractValidator.class), mapper,
                "analyzer", "graph", root.toString(), new MemoryScanStore());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> service.analyze(root.toString(), "a".repeat(40), "b".repeat(40)));
            var second = executor.submit(() -> service.analyze(root.toString(), "c".repeat(40), "d".repeat(40)));
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }
        assertThat(entered.getCount()).isZero();
    }

    @Test void concurrentValidationPatchesPreserveBothResultsInEitherCompletionOrder() throws Exception {
        for (boolean sandboxFirst : new boolean[]{true, false}) {
            var client = mock(AnalysisClient.class);
            var validations = mock(SourceValidationService.class);
            var store = new MemoryScanStore();
            ObjectNode initial = mapper.createObjectNode().put("scan_id", "scan")
                    .put("validation_status", "NOT_RUN").put("final_verdict", "BLOCK");
            store.save(initial);
            CountDownLatch entered = new CountDownLatch(2);
            CountDownLatch releaseSandbox = new CountDownLatch(1);
            CountDownLatch releaseSource = new CountDownLatch(1);
            ObjectNode sandboxResult = mapper.createObjectNode().put("status", "REJECTED");
            ObjectNode sourceResult = mapper.createObjectNode().put("status", "CONFIRMED");
            when(validations.validateSandbox(any(), eq("protected"), any())).thenAnswer(call -> {
                entered.countDown();
                assertThat(releaseSandbox.await(2, TimeUnit.SECONDS)).isTrue();
                return new SourceValidationService.ValidationPatch(sandboxResult, java.util.Map.of(), null);
            });
            when(validations.validateSource(any(), any(), any())).thenAnswer(call -> {
                entered.countDown();
                assertThat(releaseSource.await(2, TimeUnit.SECONDS)).isTrue();
                return new SourceValidationService.ValidationPatch(null, java.util.Map.of(), sourceResult);
            });
            doAnswer(call -> {
                ObjectNode latest = call.getArgument(0);
                SourceValidationService.ValidationPatch update = call.getArgument(1);
                if (update.sandboxDemonstration() != null) {
                    latest.set("sandbox_demonstration", update.sandboxDemonstration().deepCopy());
                }
                if (update.standaloneValidation() != null) {
                    latest.set("validation", update.standaloneValidation().deepCopy());
                    latest.put("validation_status", "CONFIRMED");
                }
                return null;
            }).when(validations).apply(any(), any());
            var service = new SourceScanService(client, mock(ContractValidator.class), mapper,
                    "analyzer", "graph", root.toString(), store,
                    mock(FindingEnrichmentService.class), validations);

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var sandbox = executor.submit(() -> service.validateSandbox("scan", "protected"));
                var source = executor.submit(() -> service.validateSource("scan"));
                assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
                if (sandboxFirst) {
                    releaseSandbox.countDown();
                    sandbox.get(2, TimeUnit.SECONDS);
                    releaseSource.countDown();
                    source.get(2, TimeUnit.SECONDS);
                } else {
                    releaseSource.countDown();
                    source.get(2, TimeUnit.SECONDS);
                    releaseSandbox.countDown();
                    sandbox.get(2, TimeUnit.SECONDS);
                }
            }

            JsonNode persisted = service.get("scan");
            assertThat(persisted.at("/sandbox_demonstration/status").asString())
                    .isEqualTo("REJECTED");
            assertThat(persisted.at("/validation/status").asString()).isEqualTo("CONFIRMED");
            assertThat(persisted.path("validation_status").asString()).isEqualTo("CONFIRMED");
        }
    }

    private ObjectNode envelopeForRoot() throws Exception {
        ObjectNode envelope = (ObjectNode) mapper.readTree(getClass().getResourceAsStream(
                "/contracts/ir/examples/analysis-envelope.json"));
        ((ObjectNode) envelope.path("provenance")).put("repository_path", root.toRealPath().toString());
        return envelope;
    }

    private String runGit(String... arguments) throws Exception {
        String[] command = new String[arguments.length + 1];
        command[0] = "git";
        System.arraycopy(arguments, 0, command, 1, arguments.length);
        Process process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(process.waitFor()).as(output).isZero();
        return output;
    }

    private ObjectNode envelopeWithRows(String revision, int rowCount) throws Exception {
        ObjectNode envelope = envelopeForRoot();
        var paths = (tools.jackson.databind.node.ArrayNode) envelope.at(
                "/" + revision + "/0/dependency_paths");
        ObjectNode template = ((ObjectNode) paths.get(0)).deepCopy();
        paths.removeAll();
        for (int index = 0; index < rowCount; index++) {
            paths.add(template.deepCopy().put("repository", "Repository" + index)
                    .put("resource", "Resource" + index));
        }
        return envelope;
    }

    private ObjectNode graphWithTwoFindings() {
        ObjectNode result = emptyGraphResult();
        var paths = (tools.jackson.databind.node.ArrayNode) result.at("/graph_delta/new_paths");
        for (String path : new String[]{"/one", "/two"}) {
            ObjectNode evidence = paths.addObject().put("source", "user:anonymous").put("target", "resource:Customer");
            evidence.putArray("nodes").add("user:anonymous").add("endpoint:GET:" + path).add("resource:Customer");
            evidence.putArray("edges").add("CAN_ACCESS").add("RETURNS");
        }
        ((ObjectNode) result.path("risk_result")).put("category_after", "HIGH");
        return result;
    }

    private ObjectNode emptyGraphResult() {
        ObjectNode result = mapper.createObjectNode().put("verdict", "REVIEW");
        ObjectNode delta = result.putObject("graph_delta");
        delta.putObject("before").putArray("nodes");
        ((ObjectNode) delta.path("before")).putArray("edges");
        delta.putObject("after").putArray("nodes");
        ((ObjectNode) delta.path("after")).putArray("edges");
        delta.putArray("new_paths"); delta.putArray("removed_paths");
        result.putObject("risk_result").put("category_after", "LOW").putArray("evidence");
        return result;
    }
}
