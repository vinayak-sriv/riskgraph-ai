package ai.riskgraph.platform.api;

import ai.riskgraph.platform.service.EnrichmentExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/enrichment")
public class EnrichmentQueueController {
    private final EnrichmentExecutor enrichmentExecutor;

    public EnrichmentQueueController(EnrichmentExecutor enrichmentExecutor) {
        this.enrichmentExecutor = enrichmentExecutor;
    }

    @GetMapping("/queue")
    public EnrichmentExecutor.QueueHealth queueHealth() {
        return enrichmentExecutor.health();
    }
}
