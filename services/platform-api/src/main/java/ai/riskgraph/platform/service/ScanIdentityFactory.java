package ai.riskgraph.platform.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Map;
import java.util.HexFormat;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public final class ScanIdentityFactory {
    private ScanIdentityFactory() {
    }

    public static String create(ObjectMapper mapper, JsonNode envelope,
            JsonNode graphInput, JsonNode graphResult) {
        ObjectNode identity = mapper.createObjectNode();
        identity.put("identity_version", "1");
        identity.put("repository_identity",
                envelope.at("/provenance/repository_identity").asString());
        identity.put("old_commit", envelope.at("/provenance/old_commit").asString());
        identity.put("new_commit", envelope.at("/provenance/new_commit").asString());
        identity.put("ir_schema_version", envelope.path("schema_version").asString());
        identity.put("analyzer_version", envelope.path("analyzer_version").asString());
        identity.put("analyzer_config_hash",
                envelope.path("analyzer_config_hash").asString());
        identity.put("sensitivity_policy_fingerprint",
                envelope.path("analyzer_config_hash").asString());
        identity.put("risk_policy_version",
                graphResult.at("/risk_result/policy_version").asString());
        identity.set("canonical_graph_input", canonicalize(mapper, graphInput));
        try {
            byte[] serialized = mapper.writeValueAsBytes(identity);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(serialized));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static JsonNode canonicalize(ObjectMapper mapper, JsonNode value) {
        if (value.isObject()) {
            ObjectNode result = mapper.createObjectNode();
            var names = new ArrayList<String>();
            names.addAll(value.propertyNames());
            names.stream().sorted().forEach(name ->
                    result.set(name, canonicalize(mapper, value.path(name))));
            return result;
        }
        if (value.isArray()) {
            // Decorate-sort-undecorate: sorting on a JsonNode::toString key extractor
            // re-serializes both operands on every comparison, which is megabytes
            // of throwaway strings per revision at the 2000-row cap.
            var items = new ArrayList<Map.Entry<String, JsonNode>>();
            value.forEach(item -> {
                JsonNode canonical = canonicalize(mapper, item);
                items.add(Map.entry(canonical.toString(), canonical));
            });
            items.sort(Map.Entry.comparingByKey());
            ArrayNode result = mapper.createArrayNode();
            items.forEach(entry -> result.add(entry.getValue()));
            return result;
        }
        return value.deepCopy();
    }
}
