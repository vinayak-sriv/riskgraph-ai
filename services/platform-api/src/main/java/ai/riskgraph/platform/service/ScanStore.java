package ai.riskgraph.platform.service;
import java.util.function.UnaryOperator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
public interface ScanStore {
    void save(JsonNode result);
    JsonNode get(String id);
    JsonNode update(String id, UnaryOperator<ObjectNode> mutation);
}
