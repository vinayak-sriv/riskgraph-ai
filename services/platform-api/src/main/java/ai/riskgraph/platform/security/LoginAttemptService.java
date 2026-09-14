package ai.riskgraph.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {
    private record Attempt(int failures, long firstFailureMillis, long blockedUntilMillis) {}

    private static final int MAX_TRACKED_KEYS = 10_000;
    private final ConcurrentHashMap<String, Attempt> accountAttempts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Attempt> networkAttempts = new ConcurrentHashMap<>();
    private final AtomicLong operations = new AtomicLong();
    private final Clock clock;
    private final int maxFailures;
    private final int networkMaxFailures;
    private final long windowMillis;
    private final long blockMillis;

    @Autowired
    public LoginAttemptService(
            @Value("${RISKGRAPH_LOGIN_MAX_FAILURES:5}") int maxFailures,
            @Value("${RISKGRAPH_LOGIN_IP_MAX_FAILURES:50}") int networkMaxFailures,
            @Value("${RISKGRAPH_LOGIN_WINDOW_SECONDS:300}") long windowSeconds,
            @Value("${RISKGRAPH_LOGIN_BLOCK_SECONDS:60}") long blockSeconds) {
        this(Clock.systemUTC(), maxFailures, networkMaxFailures, windowSeconds, blockSeconds);
    }

    LoginAttemptService(Clock clock, int maxFailures, long windowSeconds, long blockSeconds) {
        this(clock, maxFailures, maxFailures * 10, windowSeconds, blockSeconds);
    }

    LoginAttemptService(Clock clock, int maxFailures, int networkMaxFailures,
            long windowSeconds, long blockSeconds) {
        this.clock = clock;
        this.maxFailures = Math.max(2, maxFailures);
        this.networkMaxFailures = Math.max(this.maxFailures, networkMaxFailures);
        this.windowMillis = Math.max(10, windowSeconds) * 1_000;
        this.blockMillis = Math.max(10, blockSeconds) * 1_000;
    }

    public boolean blocked(HttpServletRequest request) {
        cleanupOccasionally();
        return blocked(accountAttempts, username(request))
                || blocked(networkAttempts, request.getRemoteAddr());
    }

    private boolean blocked(ConcurrentHashMap<String, Attempt> attempts, String key) {
        Attempt attempt = attempts.get(key);
        if (attempt == null) return false;
        long now = clock.millis();
        if (attempt.blockedUntilMillis() > now) return true;
        if (now - attempt.firstFailureMillis() > windowMillis) attempts.remove(key, attempt);
        return false;
    }

    public void failed(HttpServletRequest request) {
        long now = clock.millis();
        recordFailure(accountAttempts, username(request), maxFailures, now);
        recordFailure(networkAttempts, request.getRemoteAddr(), networkMaxFailures, now);
    }

    private void recordFailure(ConcurrentHashMap<String, Attempt> attempts, String key,
            int failureLimit, long now) {
        synchronized (attempts) {
            if (attempts.size() >= MAX_TRACKED_KEYS && !attempts.containsKey(key)) {
                if (!evictOldestUnblocked(attempts, now)) return;
            }
            attempts.compute(key, (ignored, previous) -> {
                int failures = previous == null || now - previous.firstFailureMillis() > windowMillis
                        ? 1 : previous.failures() + 1;
                long first = failures == 1 ? now : previous.firstFailureMillis();
                long blockedUntil = failures >= failureLimit ? now + blockMillis : 0;
                return new Attempt(failures, first, blockedUntil);
            });
        }
    }

    public void succeeded(HttpServletRequest request) {
        accountAttempts.remove(username(request));
    }

    private String username(HttpServletRequest request) {
        String username = request.getParameter("username");
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }

    private boolean evictOldestUnblocked(ConcurrentHashMap<String, Attempt> attempts, long now) {
        Map.Entry<String, Attempt> oldest = attempts.entrySet().stream()
                .filter(entry -> entry.getValue().blockedUntilMillis() <= now)
                .min(Map.Entry.comparingByValue(
                        java.util.Comparator.comparingLong(Attempt::firstFailureMillis)))
                .orElse(null);
        return oldest != null && attempts.remove(oldest.getKey(), oldest.getValue());
    }

    private void cleanupOccasionally() {
        if ((operations.incrementAndGet() & 255) != 0) return;
        long expiry = clock.millis() - windowMillis;
        cleanup(accountAttempts, expiry);
        cleanup(networkAttempts, expiry);
    }

    private void cleanup(ConcurrentHashMap<String, Attempt> attempts, long expiry) {
        long now = clock.millis();
        attempts.entrySet().removeIf(entry -> entry.getValue().blockedUntilMillis() <= now
                && entry.getValue().firstFailureMillis() < expiry);
    }
}
