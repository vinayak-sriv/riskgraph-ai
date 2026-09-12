package ai.riskgraph.analyzer.api;

import java.time.Instant;
import java.nio.file.InvalidPathException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import ai.riskgraph.analyzer.source.SourceAcquisitionException;
import ai.riskgraph.analyzer.service.AnalysisException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(SourceAcquisitionException.class)
    ProblemDetail handleSourceError(SourceAcquisitionException error) {
        return problem("Source acquisition rejected", error.code(), error.getMessage());
    }

    @ExceptionHandler(AnalysisException.class)
    ProblemDetail handleAnalysisError(AnalysisException error) {
        if ("ANALYZER_BUSY".equals(error.code())) {
            return problem(HttpStatus.TOO_MANY_REQUESTS, "Analysis rejected", error.code(), error.getMessage());
        }
        return problem("Analysis failed", error.code(), error.getMessage());
    }

    @ExceptionHandler(InvalidPathException.class)
    ProblemDetail handleInvalidPath(InvalidPathException error) {
        return problem("Source acquisition rejected", "INVALID_REPOSITORY_PATH", "Repository path is invalid");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleInvalidRequest(MethodArgumentNotValidException error) {
        return problem("Analysis request rejected", "INVALID_REQUEST", "Required request fields are missing or blank");
    }

    private ProblemDetail problem(String title, String code, String message) {
        return problem(HttpStatus.BAD_REQUEST, title, code, message);
    }

    private ProblemDetail problem(HttpStatus status, String title, String code, String message) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setTitle(title);
        detail.setProperty("code", code);
        detail.setProperty("timestamp", Instant.now());
        return detail;
    }
}
