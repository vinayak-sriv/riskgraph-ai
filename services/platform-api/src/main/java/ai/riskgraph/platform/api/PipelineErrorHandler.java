package ai.riskgraph.platform.api;

import java.util.Map;
import ai.riskgraph.platform.service.PipelineException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class PipelineErrorHandler {
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<?> database(Exception ex) {
        return ResponseEntity.status(503).body(Map.of("status", "FAILED", "code", "PERSISTENCE_UNAVAILABLE",
            "message", "Scan persistence is unavailable", "final_verdict", "REVIEW"));
    }
    @ExceptionHandler(PipelineException.class)
    public ResponseEntity<?> pipeline(PipelineException ex) {
        if (ex.code.equals("UNSUPPORTED_FRAMEWORK")) {
            return ResponseEntity.status(ex.status).body(Map.of("status", "FAILED", "code", ex.code,
                    "message", ex.getMessage()));
        }
        return ResponseEntity.status(ex.status).body(Map.of("status", "FAILED", "code", ex.code,
            "message", ex.getMessage(), "final_verdict", "REVIEW"));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<?> invalid(Exception ex) {
        return ResponseEntity.badRequest().body(Map.of("status", "FAILED", "code", "INVALID_REQUEST",
            "message", "Request does not match the API contract", "final_verdict", "REVIEW"));
    }
}
