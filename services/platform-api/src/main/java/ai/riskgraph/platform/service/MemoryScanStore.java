package ai.riskgraph.platform.service;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
@Component @Profile("local")
public class MemoryScanStore implements ScanStore {
    private final ConcurrentHashMap<String, JsonNode> results = new ConcurrentHashMap<>();
    public void save(JsonNode result) {
        if (results.size() >= 1000 && !results.containsKey(result.path("scan_id").asString()))
            throw new PipelineException("LOCAL_STORE_FULL", 503, "Ephemeral demo storage is full; use PostgreSQL");
        results.put(result.path("scan_id").asString(), result.deepCopy());
    }
    public JsonNode get(String id) { return results.get(id); }
}
