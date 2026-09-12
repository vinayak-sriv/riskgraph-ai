package ai.riskgraph.analyzer.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import ai.riskgraph.analyzer.service.AnalysisService;
import ai.riskgraph.analyzer.source.SourceAcquisitionException;

class AnalyzeControllerTest {
    @Test
    void sourceFailureReturnsStructuredProblemDetail() throws Exception {
        AnalysisService service = mock(AnalysisService.class);
        when(service.analyze(any(), anyString(), anyString()))
                .thenThrow(new SourceAcquisitionException("INVALID_COMMIT", "Commit must be a full SHA"));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AnalyzeController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        mockMvc.perform(post("/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "repository_path": ".",
                                  "old_commit": "abc1234",
                                  "new_commit": "def5678"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COMMIT"))
                .andExpect(jsonPath("$.title").value("Source acquisition rejected"));
    }
}
