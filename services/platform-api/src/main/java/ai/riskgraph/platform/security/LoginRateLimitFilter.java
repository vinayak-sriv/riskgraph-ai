package ai.riskgraph.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {
    private final LoginAttemptService attempts;

    public LoginRateLimitFilter(LoginAttemptService attempts) {
        this.attempts = attempts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if ("POST".equals(request.getMethod()) && "/auth/login".equals(request.getServletPath())
                && attempts.blocked(request)) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.setHeader("Retry-After", "60");
            response.getWriter().write("{\"code\":\"LOGIN_RATE_LIMITED\",\"message\":\"Try again later\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
