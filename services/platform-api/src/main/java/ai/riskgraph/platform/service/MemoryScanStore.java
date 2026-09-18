package ai.riskgraph.platform.service;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
@Component @Profile("local")
public class MemoryScanStore implements ScanStore {
    private final ConcurrentHashMap<String, JsonNode> results = new ConcurrentHashMap<>();
    public void save(JsonNode result) {
        if (results.size() >= 1000 && !results.containsKey(result.path("scan_id").asString()))
            throw new PipelineException("LOCAL_STORE_FULL", 503, "Ephemeral demo storage is full; use PostgreSQL");
        results.put(result.path("scan_id").asString(), result.deepCopy());
    }
    public JsonNode get(String id) { return results.get(id); }
    public java.util.Map<String, Summary> summaries(java.util.Collection<String> ids) {
        var summaries = new java.util.HashMap<String, Summary>();
        for (String id : ids) {
            JsonNode result = results.get(id);
            if (result != null) summaries.put(id, new Summary(
                    result.at("/risk_result/risk_delta").asInt(),
                    result.path("final_verdict").asString()));
        }
        return java.util.Map.copyOf(summaries);
    }
    public JsonNode update(String id, UnaryOperator<ObjectNode> mutation) {
        AtomicReference<JsonNode> updated = new AtomicReference<>();
        results.computeIfPresent(id, (ignored, existing) -> {
            ObjectNode next = mutation.apply((ObjectNode) existing.deepCopy());
            JsonNode stored = next.deepCopy();
            updated.set(stored.deepCopy());
            return stored;
        });
        return updated.get();
    }
}
