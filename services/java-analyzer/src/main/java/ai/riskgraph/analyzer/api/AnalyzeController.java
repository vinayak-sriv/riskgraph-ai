package ai.riskgraph.analyzer.api;

import java.nio.file.Path;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import ai.riskgraph.analyzer.model.AnalysisModels.AnalysisResponse;
import ai.riskgraph.analyzer.service.AnalysisService;

@RestController
public class AnalyzeController {
    private final AnalysisService analysisService;

    public AnalyzeController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/analyze")
    public AnalysisResponse analyze(@Valid @RequestBody AnalyzeRequest request) {
        return analysisService.analyze(Path.of(request.repository_path()), request.old_commit(), request.new_commit());
    }

    public record AnalyzeRequest(
            @NotBlank String repository_path,
            @NotBlank String old_commit,
            @NotBlank String new_commit
    ) {
    }
}
