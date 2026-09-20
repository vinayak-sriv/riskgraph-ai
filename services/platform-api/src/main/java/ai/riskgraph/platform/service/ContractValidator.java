package ai.riskgraph.platform.service;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SpecificationVersion;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class ContractValidator {
    private final SchemaRegistry registry = SchemaRegistry.withDefaultDialect(
        SpecificationVersion.DRAFT_2020_12, builder -> builder.schemaIdResolvers(resolvers ->
            resolvers.mapPrefix("https://riskgraph.ai/contracts/", "classpath:contracts/")));
    // The schemas are immutable classpath resources and the largest is ~17KB;
    // compile each once rather than on every call (8+ per scan).
    private final Map<String, Schema> compiled = new ConcurrentHashMap<>();

    public void validate(String contract, JsonNode value) {
        var schema = compiled.computeIfAbsent(contract, name ->
            registry.getSchema(SchemaLocation.of("classpath:contracts/" + name)));
        if (!schema.validate(value).isEmpty()) {
            throw new PipelineException("INVALID_DEPENDENCY_RESPONSE", 502,
                "Dependency response does not match " + contract);
        }
    }
}
