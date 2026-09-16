package ai.riskgraph.platform.service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;

import ai.riskgraph.platform.client.GraphRiskClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DemoScenarioService {
    private final GraphRiskClient graphRiskClient;
    private final ObjectMapper objectMapper;
    private final boolean dynamicEnabled;
    private final AtomicInteger activeDynamicRequests = new AtomicInteger();
    private final ArrayDeque<Instant> recentDynamicRequests = new ArrayDeque<>();
    @Value("${RISKGRAPH_DYNAMIC_DEMO_MAX_CONCURRENCY:2}")
    private int maxDynamicConcurrency = 2;
    @Value("${RISKGRAPH_DYNAMIC_DEMO_REQUESTS_PER_MINUTE:30}")
    private int maxDynamicRequestsPerMinute = 30;

    public DemoScenarioService(GraphRiskClient graphRiskClient, ObjectMapper objectMapper,
            @Value("${riskgraph.demo.dynamic-enabled:false}") boolean dynamicEnabled) {
        this.graphRiskClient = graphRiskClient;
        this.objectMapper = objectMapper;
        this.dynamicEnabled = dynamicEnabled;
    }

    public JsonNode analyzeAuthorizationRemoval() {
        return analyzeScenario("authorization-removal");
    }

    public JsonNode analyzeScenario(String scenario) {
        if (!isKnownScenario(scenario)) {
            throw new PipelineException("UNKNOWN_SCENARIO", 404, "Unknown demo scenario");
        }
        if (!dynamicEnabled) {
            return readFixture("demo/fixtures.json").path(scenario).deepCopy();
        }
        checkDynamicRateLimit();
        if (activeDynamicRequests.incrementAndGet() > Math.max(1, maxDynamicConcurrency)) {
            activeDynamicRequests.decrementAndGet();
            throw new PipelineException("DEMO_CONCURRENCY_LIMIT", 429,
                    "Dynamic demo analysis is temporarily busy");
        }
        try {
            return analyzeDynamicScenario(scenario);
        } finally {
            activeDynamicRequests.decrementAndGet();
        }
    }

    private JsonNode analyzeDynamicScenario(String scenario) {
        ObjectNode request = objectMapper.createObjectNode();
        ObjectNode protectedRow = (ObjectNode) readFixture("contracts/ir/examples/auth-removal-before.json");
        ObjectNode publicRow = (ObjectNode) readFixture("contracts/ir/examples/auth-removal-after.json");
        var before = request.putArray("before");
        var after = request.putArray("after");
        switch (scenario) {
            case "authorization-removal" -> { before.add(protectedRow); after.add(publicRow); }
            case "safe-change" -> { before.add(protectedRow); after.add(protectedRow.deepCopy()); }
            case "new-public-sensitive-endpoint" -> after.add(publicRow);
            case "sensitive-resource-exposure" -> {
                before.add(publicRow.deepCopy().put("resource", "PublicCatalog").put("sensitivity", "LOW")
                    .put("repository", "CatalogRepository"));
                after.add(publicRow);
            }
            default -> throw new IllegalStateException("Scenario allowlist changed during analysis");
        }
        ObjectNode result = (ObjectNode) graphRiskClient.analyze(request);
        result.put("scenario", scenario).put("mode", "FIXTURE");
        result.put("pre_validation_verdict", result.path("verdict").asString());
        result.put("final_verdict", result.path("verdict").asString()).put("validation_status", "NOT_RUN");
        return result;
    }

    private synchronized void checkDynamicRateLimit() {
        Instant cutoff = Instant.now().minusSeconds(60);
        while (!recentDynamicRequests.isEmpty()
                && recentDynamicRequests.getFirst().isBefore(cutoff)) {
            recentDynamicRequests.removeFirst();
        }
        if (recentDynamicRequests.size() >= Math.max(1, maxDynamicRequestsPerMinute)) {
            throw new PipelineException("DEMO_RATE_LIMIT", 429,
                    "Dynamic demo request limit reached");
        }
        recentDynamicRequests.addLast(Instant.now());
    }

    private boolean isKnownScenario(String scenario) {
        return scenario.equals("authorization-removal") || scenario.equals("safe-change")
                || scenario.equals("new-public-sensitive-endpoint")
                || scenario.equals("sensitive-resource-exposure");
    }

    private JsonNode readFixture(String path) {
        try (var input = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readTree(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load demo fixture: " + path, exception);
        }
    }
}
