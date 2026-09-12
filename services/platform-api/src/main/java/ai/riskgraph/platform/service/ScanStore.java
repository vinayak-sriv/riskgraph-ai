package ai.riskgraph.platform.service;
import tools.jackson.databind.JsonNode;
public interface ScanStore {
    void save(JsonNode result);
    JsonNode get(String id);
}
