package ai.riskgraph.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {
    private record Attempt(int failures, long firstFailureMillis, long blockedUntilMillis) {}

    private static final int MAX_TRACKED_KEYS = 10_000;
    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final AtomicLong operations = new AtomicLong();
    private final Clock clock;
    private final int maxFailures;
    private final long windowMillis;
    private final long blockMillis;

    @Autowired
    public LoginAttemptService(
            @Value("${RISKGRAPH_LOGIN_MAX_FAILURES:5}") int maxFailures,
            @Value("${RISKGRAPH_LOGIN_WINDOW_SECONDS:300}") long windowSeconds,
            @Value("${RISKGRAPH_LOGIN_BLOCK_SECONDS:60}") long blockSeconds) {
        this(Clock.systemUTC(), maxFailures, windowSeconds, blockSeconds);
    }

    LoginAttemptService(Clock clock, int maxFailures, long windowSeconds, long blockSeconds) {
        this.clock = clock;
        this.maxFailures = Math.max(2, maxFailures);
        this.windowMillis = Math.max(10, windowSeconds) * 1_000;
        this.blockMillis = Math.max(10, blockSeconds) * 1_000;
    }

    public boolean blocked(HttpServletRequest request) {
        cleanupOccasionally();
        String key = key(request);
        Attempt attempt = attempts.get(key);
        if (attempt == null) return attempts.size() >= MAX_TRACKED_KEYS;
        long now = clock.millis();
        if (attempt.blockedUntilMillis() > now) return true;
        if (now - attempt.firstFailureMillis() > windowMillis) attempts.remove(key, attempt);
        return false;
    }

    public void failed(HttpServletRequest request) {
        long now = clock.millis();
        String key = key(request);
        if (attempts.size() >= MAX_TRACKED_KEYS && !attempts.containsKey(key)) return;
        attempts.compute(key, (ignored, previous) -> {
            int failures = previous == null || now - previous.firstFailureMillis() > windowMillis
                    ? 1 : previous.failures() + 1;
            long first = failures == 1 ? now : previous.firstFailureMillis();
            long blockedUntil = failures >= maxFailures ? now + blockMillis : 0;
            return new Attempt(failures, first, blockedUntil);
        });
    }

    public void succeeded(HttpServletRequest request) {
        attempts.remove(key(request));
    }

    private String key(HttpServletRequest request) {
        String username = request.getParameter("username");
        String normalized = username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
        return normalized + "\u0000" + request.getRemoteAddr();
    }

    private void cleanupOccasionally() {
        if ((operations.incrementAndGet() & 255) != 0) return;
        long expiry = clock.millis() - windowMillis;
        attempts.entrySet().removeIf(entry -> entry.getValue().blockedUntilMillis() <= clock.millis()
                && entry.getValue().firstFailureMillis() < expiry);
    }
}
