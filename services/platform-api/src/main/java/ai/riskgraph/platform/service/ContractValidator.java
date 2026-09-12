package ai.riskgraph.platform.service;

import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SpecificationVersion;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class ContractValidator {
    private final SchemaRegistry registry = SchemaRegistry.withDefaultDialect(
        SpecificationVersion.DRAFT_2020_12, builder -> builder.schemaIdResolvers(resolvers ->
            resolvers.mapPrefix("https://riskgraph.ai/contracts/", "classpath:contracts/")));

    public void validate(String contract, JsonNode value) {
        var schema = registry.getSchema(SchemaLocation.of("classpath:contracts/" + contract));
        if (!schema.validate(value).isEmpty()) {
            throw new PipelineException("INVALID_DEPENDENCY_RESPONSE", 502,
                "Dependency response does not match " + contract);
        }
    }
}
