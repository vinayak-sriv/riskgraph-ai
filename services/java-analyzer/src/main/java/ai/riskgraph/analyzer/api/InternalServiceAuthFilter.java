package ai.riskgraph.analyzer.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class InternalServiceAuthFilter extends OncePerRequestFilter {
    private final String expectedToken;

    public InternalServiceAuthFilter(@Value("${RISKGRAPH_SERVICE_TOKEN:}") String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().equals("/health")
                || request.getRequestURI().startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader("X-RiskGraph-Service-Token");
        if (expectedToken.isBlank()) {
            reject(response, 503, "SERVICE_AUTH_NOT_CONFIGURED");
            return;
        }
        if (supplied == null || !MessageDigest.isEqual(
                supplied.getBytes(StandardCharsets.UTF_8), expectedToken.getBytes(StandardCharsets.UTF_8))) {
            reject(response, 401, "INVALID_SERVICE_CREDENTIAL");
            return;
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":\"" + code + "\"}");
    }
}
