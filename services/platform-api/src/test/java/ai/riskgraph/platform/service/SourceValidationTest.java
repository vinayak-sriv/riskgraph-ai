package ai.riskgraph.platform.service;

import ai.riskgraph.platform.client.AnalysisClient;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SourceValidationTest {
    @TempDir Path root;
    final JsonMapper mapper=JsonMapper.builder().build();

    private SourceScanService service(AnalysisClient client) throws Exception {
        ObjectNode pair=mapper.createObjectNode().put("repository_path",root.toString())
            .put("old_commit","a".repeat(40)).put("new_commit","b".repeat(40));
        ObjectNode manifest=mapper.createObjectNode();
        manifest.putObject("scenarios").set("authorization-removal",pair);
        Path file=root.resolve("manifest.json"); Files.writeString(file,manifest.toString());
        var store=new MemoryScanStore();
        ObjectNode scan=mapper.createObjectNode().put("scan_id","test").put("pre_validation_verdict","BLOCK");
        scan.put("status", "COMPLETE");
        scan.putArray("findings").addObject().put("finding_id", "supported")
                .put("method", "GET").put("path", "/admin/export")
                .put("validation_capability", "SUPPORTED");
        scan.set("provenance",pair); store.save(scan);
        var service=new SourceScanService(client,new ContractValidator(),mapper,"analyzer","graph",root.toString(),store);
        ReflectionTestUtils.setField(service,"sandboxManifest",file.toString());
        return service;
    }

    @Test void confirmedResultRequiresMatchingCommitAndSuccessfulCleanup() throws Exception {
        var client=mock(AnalysisClient.class);
        var validation=mapper.createObjectNode().put("status","CONFIRMED").put("confirmed",true)
            .put("reason_code","HTTP_AUTH_PROBE").put("sandbox_revision","vulnerable")
            .put("cleanup_complete",true).put("actual_status", 200).put("source_commit","b".repeat(40))
            .put("container_image_id", "sha256:" + "c".repeat(64))
            .put("probe_image_id", "sha256:" + "d".repeat(64))
            .put("response_sha256", "e".repeat(64));
        when(client.post(any(),eq("/validation/http"),any())).thenAnswer(call -> {
            ObjectNode request=call.getArgument(2);
            assertThat(request.path("expected_commit").asString()).isEqualTo("b".repeat(40));
            return validation;
        });
        var service=service(client);
        assertThat(service.validateSource("test").path("validation_status").asString()).isEqualTo("CONFIRMED");
        validation.put("source_commit","c".repeat(40));
        var failed=service.validateSource("test");
        assertThat(failed.at("/validation/reason_code").asString()).isEqualTo("SANDBOX_IDENTITY_MISMATCH");
        assertThat(failed.path("final_verdict").asString()).isEqualTo("BLOCK");
        validation.put("source_commit","b".repeat(40)).put("cleanup_complete",false);
        assertThat(service.validateSource("test").path("validation_status").asString()).isEqualTo("ERROR");
    }

    @Test void pythonScanSelectsTheCommitBoundPythonSandbox() throws Exception {
        var client = mock(AnalysisClient.class);
        var service = service(client);
        ObjectNode scan = (ObjectNode) service.get("test");
        scan.put("analyzer_language", "python");
        var store = (MemoryScanStore) ReflectionTestUtils.getField(service, "store");
        store.save(scan);
        when(client.post(any(), eq("/validation/http"), any())).thenAnswer(call -> {
            ObjectNode request = call.getArgument(2);
            assertThat(request.path("language").asString()).isEqualTo("python");
            return mapper.createObjectNode().put("status", "REJECTED").put("confirmed", false)
                    .put("reason_code", "HTTP_AUTH_PROBE").put("sandbox_revision", "vulnerable")
                    .put("cleanup_complete", true).put("actual_status", 403)
                    .put("source_commit", "b".repeat(40));
        });

        assertThat(service.validateSource("test").path("validation_status").asString())
                .isEqualTo("REJECTED");
    }

    @Test void confirmedResultRequiresImmutableRuntimeProvenance() throws Exception {
        var client = mock(AnalysisClient.class);
        var validation = mapper.createObjectNode().put("status", "CONFIRMED").put("confirmed", true)
                .put("reason_code", "HTTP_AUTH_PROBE").put("sandbox_revision", "vulnerable")
                .put("cleanup_complete", true).put("actual_status", 200)
                .put("source_commit", "b".repeat(40))
                .put("container_image_id", "sha256:" + "c".repeat(64))
                .put("probe_image_id", "mutable:latest")
                .put("response_sha256", "e".repeat(64));
        when(client.post(any(), eq("/validation/http"), any())).thenReturn(validation);

        JsonNode failed = service(client).validateSource("test");

        assertThat(failed.path("validation_status").asString()).isEqualTo("ERROR");
        assertThat(failed.at("/validation/reason_code").asString())
                .isEqualTo("SANDBOX_IDENTITY_MISMATCH");
    }

    @Test void unregisteredPairAndUnavailableDockerCannotConfirm() throws Exception {
        var client=mock(AnalysisClient.class);
        var service=service(client);
        when(client.post(any(),any(),any())).thenThrow(new PipelineException("DEPENDENCY_TIMEOUT",504,"Timeout"));
        var failed=service.validateSource("test");
        assertThat(failed.path("final_verdict").asString()).isEqualTo("BLOCK");
        assertThat(failed.path("validation_status").asString()).isEqualTo("ERROR");
        clearInvocations(client);
        Files.writeString(root.resolve("manifest.json"),"{}");
        assertThatThrownBy(() -> service.validateSource("test")).isInstanceOf(PipelineException.class);
        verifyNoInteractions(client);
    }

    @Test void zeroFindingsDoNotInvokeTheValidationService() throws Exception {
        var client = mock(AnalysisClient.class);
        var service = service(client);
        ObjectNode scan = (ObjectNode) service.get("test");
        scan.putArray("findings");
        var store = (MemoryScanStore) ReflectionTestUtils.getField(service, "store");
        store.save(scan);

        JsonNode result = service.validateSource("test");

        assertThat(result.path("validation_status").asString()).isEqualTo("NOT_RUN");
        assertThat(result.at("/validation/reason_code").asString())
                .isEqualTo("NO_VALIDATABLE_FINDING");
        assertThat(result.path("status").asString()).isEqualTo("COMPLETE");
        verifyNoInteractions(client);
    }

    @Test void duplicateRouteFindingsShareOneBoundValidationResult() throws Exception {
        var client = mock(AnalysisClient.class);
        var service = service(client);
        ObjectNode scan = (ObjectNode) service.get("test");
        var findings = scan.putArray("findings");
        findings.addObject().put("finding_id", "finding-one").put("method", "GET")
                .put("path", "/admin/export");
        findings.addObject().put("finding_id", "finding-two").put("method", "GET")
                .put("path", "/admin/export");
        var store = (MemoryScanStore) org.springframework.test.util.ReflectionTestUtils.getField(service, "store");
        store.save(scan);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        when(client.post(any(), eq("/validation/http"), any())).thenAnswer(call -> {
            boolean first = calls.getAndIncrement() == 0;
            return mapper.createObjectNode().put("status", first ? "CONFIRMED" : "REJECTED")
                    .put("confirmed", first).put("reason_code", "HTTP_AUTH_PROBE")
                    .put("sandbox_revision", "vulnerable").put("cleanup_complete", true)
                    .put("actual_status", first ? 200 : 403)
                    .put("source_commit", "b".repeat(40))
                    .put("container_image_id", "sha256:" + "c".repeat(64))
                    .put("probe_image_id", "sha256:" + "d".repeat(64))
                    .put("response_sha256", "e".repeat(64));
        });

        JsonNode validated = service.validateSource("test");

        assertThat(validated.at("/findings/0/finding_id").asString()).isEqualTo("finding-one");
        assertThat(validated.at("/findings/0/validation/status").asString()).isEqualTo("CONFIRMED");
        assertThat(validated.at("/findings/1/finding_id").asString()).isEqualTo("finding-two");
        assertThat(validated.at("/findings/1/validation/status").asString()).isEqualTo("CONFIRMED");
        assertThat(validated.path("validation_status").asString()).isEqualTo("CONFIRMED");
        verify(client, times(1)).post(any(), eq("/validation/http"), any());
    }

    @Test void mixedConfirmedAndNotRunFindingsBlockWithoutClaimingFullConfirmation() throws Exception {
        var client = mock(AnalysisClient.class);
        var service = service(client);
        ObjectNode scan = (ObjectNode) service.get("test");
        var findings = scan.putArray("findings");
        findings.addObject().put("finding_id", "confirmed").put("method", "GET")
                .put("path", "/admin/export").put("validation_capability", "SUPPORTED");
        findings.addObject().put("finding_id", "unsupported").put("method", "POST")
                .put("path", "/other").put("validation_capability", "UNSUPPORTED");
        var store = (MemoryScanStore) org.springframework.test.util.ReflectionTestUtils.getField(service, "store");
        store.save(scan);
        when(client.post(any(), eq("/validation/http"), any())).thenReturn(mapper.createObjectNode()
                .put("status", "CONFIRMED").put("confirmed", true).put("reason_code", "HTTP_AUTH_PROBE")
                .put("sandbox_revision", "vulnerable").put("cleanup_complete", true)
                .put("actual_status", 200)
                .put("source_commit", "b".repeat(40))
                .put("container_image_id", "sha256:" + "c".repeat(64))
                .put("probe_image_id", "sha256:" + "d".repeat(64))
                .put("response_sha256", "e".repeat(64)));

        JsonNode validated = service.validateSource("test");

        assertThat(validated.path("validation_status").asString()).isEqualTo("INCONCLUSIVE");
        assertThat(validated.path("final_verdict").asString()).isEqualTo("BLOCK");
        assertThat(validated.at("/validation/confirmed").asBoolean()).isFalse();
        assertThat(validated.at("/findings/0/validation/status").asString()).isEqualTo("CONFIRMED");
        assertThat(validated.at("/findings/1/validation/status").asString()).isEqualTo("NOT_RUN");
        assertThat(validated.at("/findings/0/validation_capability").asString())
                .isEqualTo("SUPPORTED");
        assertThat(validated.at("/findings/1/validation_capability").asString())
                .isEqualTo("UNSUPPORTED");
    }

    @Test void aggregatedRejectionPreservesObserved401WithoutInventing403() throws Exception {
        var client = mock(AnalysisClient.class);
        var service = service(client);
        ObjectNode scan = (ObjectNode) service.get("test");
        var findings = scan.putArray("findings");
        findings.addObject().put("finding_id", "one").put("method", "GET")
                .put("path", "/admin/export").put("validation_capability", "SUPPORTED");
        findings.addObject().put("finding_id", "two").put("method", "GET")
                .put("path", "/admin/export").put("validation_capability", "SUPPORTED");
        var store = (MemoryScanStore) ReflectionTestUtils.getField(service, "store");
        store.save(scan);
        when(client.post(any(), eq("/validation/http"), any())).thenReturn(mapper.createObjectNode()
                .put("status", "REJECTED").put("confirmed", false)
                .put("reason_code", "HTTP_AUTH_PROBE").put("sandbox_revision", "vulnerable")
                .put("cleanup_complete", true).put("actual_status", 401)
                .put("source_commit", "b".repeat(40)));

        JsonNode validated = service.validateSource("test");

        assertThat(validated.path("validation_status").asString()).isEqualTo("REJECTED");
        assertThat(validated.at("/validation/actual_status").isMissingNode()).isTrue();
        assertThat(validated.at("/validation/observed_http_statuses/0").asInt()).isEqualTo(401);
        verify(client, times(1)).post(any(), eq("/validation/http"), any());
    }
}
