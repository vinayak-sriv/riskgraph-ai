package ai.riskgraph.analyzer.api;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnalyzeController {
    @PostMapping("/analyze")
    public AnalyzeResponse analyze(@Valid @RequestBody AnalyzeRequest request) {
        return new AnalyzeResponse(List.of(), List.of(), List.of());
    }

    public record AnalyzeRequest(
            @NotBlank String repository_path,
            @NotBlank String old_commit,
            @NotBlank String new_commit
    ) {
    }

    public record AnalyzeResponse(
            List<EndpointIr> before,
            List<EndpointIr> after,
            List<String> changed_files
    ) {
    }

    public record EndpointIr(
            String endpoint,
            String method,
            String controller,
            boolean authentication,
            String required_role,
            String service,
            String repository,
            String resource,
            String sensitivity
    ) {
    }
}
