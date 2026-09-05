package ai.riskgraph.platform.api;

import ai.riskgraph.platform.service.DemoScenarioService;
import tools.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoScenarioController {
    private final DemoScenarioService demoScenarioService;

    public DemoScenarioController(DemoScenarioService demoScenarioService) {
        this.demoScenarioService = demoScenarioService;
    }

    @GetMapping("/demo/authorization-removal")
    public JsonNode authorizationRemoval() {
        return demoScenarioService.analyzeAuthorizationRemoval();
    }
}
