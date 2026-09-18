package ai.riskgraph.platform.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Polls the transactional outbox and delivers due notifications. Two steps,
 * each its own short transaction (a send must never happen inside a lock-held
 * transaction): fan an outbox event out to one notification_deliveries row
 * per resolved recipient (idempotent, unique on event+user+channel), then
 * attempt due delivery rows. Bounded retries with backoff, a per-user rate
 * limit, and a live access recheck via NotificationRecipientResolver.
 * AGENTS.md Section 8 item 2.
 */
@Component
@Profile("!local")
public class NotificationDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationDispatcher.class);
    private static final int MAX_ATTEMPTS = 5;
    private static final int BATCH_SIZE = 20;

    private final JdbcTemplate db;
    private final ObjectMapper mapper;
    private final NotificationRecipientResolver recipients;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final String fromAddress;
    private final String dashboardOrigin;
    // ponytail: per-instance in-memory limiter; move to a shared store (e.g. Redis)
    // if this ever runs as more than one dispatcher instance.
    private final RateLimiter rateLimiter = new RateLimiter(20, Duration.ofMinutes(1));

    public NotificationDispatcher(JdbcTemplate db, ObjectMapper mapper,
            NotificationRecipientResolver recipients, ObjectProvider<JavaMailSender> mailSender,
            @Value("${riskgraph.notifications.email.from:notifications@riskgraph.local}") String fromAddress,
            @Value("${riskgraph.web.allowed-origin}") String dashboardOrigin) {
        this.db = db;
        this.mapper = mapper;
        this.recipients = recipients;
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.dashboardOrigin = dashboardOrigin.replaceAll("/+$", "");
    }

    @Scheduled(fixedDelayString = "${riskgraph.notifications.poll-interval-ms:5000}")
    public void poll() {
        enqueueDueEvents();
        attemptDueDeliveries();
    }

    @Transactional
    void enqueueDueEvents() {
        List<OutboxEvent> due = db.query("""
                SELECT event_id, scan_id, payload::text AS payload FROM notification_outbox
                WHERE status='PENDING' AND next_attempt_at <= now()
                ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED
                """, this::mapOutboxEvent, BATCH_SIZE);
        for (OutboxEvent event : due) {
            JsonNode decision = event.payload().path("decision");
            var targets = recipients.resolve(event.scanId(),
                    decision.path("repository").asString(), decision.path("final_verdict").asString());
            for (var target : targets) {
                db.update("""
                        INSERT INTO notification_deliveries(event_id,user_id,channel)
                        VALUES (?,?,?) ON CONFLICT (event_id,user_id,channel) DO NOTHING
                        """, event.eventId(), target.userId(), target.channel());
            }
            // "DELIVERED" here means fanned out to notification_deliveries, not that every
            // recipient has received it — notification_deliveries is authoritative for that.
            db.update("UPDATE notification_outbox SET status='DELIVERED', updated_at=now() WHERE event_id=?",
                    event.eventId());
        }
    }

    @Transactional
    void attemptDueDeliveries() {
        List<Delivery> due = db.query("""
                SELECT d.id, d.attempts, d.user_id, d.channel, o.payload::text AS payload,
                       u.username, u.email
                FROM notification_deliveries d
                JOIN notification_outbox o ON o.event_id = d.event_id
                JOIN users u ON u.id = d.user_id
                WHERE d.status='PENDING' AND d.next_attempt_at <= now()
                ORDER BY d.next_attempt_at, d.id LIMIT ? FOR UPDATE SKIP LOCKED
                """, this::mapDelivery, BATCH_SIZE);
        for (Delivery delivery : due) attempt(delivery);
    }

    private void attempt(Delivery delivery) {
        if (!rateLimiter.allow(delivery.userId())) return; // stays PENDING, retried next poll
        if ("IN_APP".equals(delivery.channel())) {
            markSent(delivery.id());
            return;
        }
        if (delivery.email() == null || delivery.email().isBlank()) {
            markFailed(delivery.id(), "NO_EMAIL_ADDRESS");
            return;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            markFailed(delivery.id(), "EMAIL_NOT_CONFIGURED");
            return;
        }
        try {
            sender.send(emailMessage(delivery));
            markSent(delivery.id());
        } catch (MailException error) {
            int attempts = delivery.attempts() + 1;
            LOG.warn("notification_email_failed delivery_id={} attempts={}", delivery.id(), attempts, error);
            if (attempts >= MAX_ATTEMPTS) {
                markFailed(delivery.id(), "EMAIL_SEND_FAILED");
            } else {
                Instant next = Instant.now().plusSeconds((long) Math.pow(2, attempts) * 60);
                db.update("""
                        UPDATE notification_deliveries
                        SET attempts=?, next_attempt_at=?, failed_reason=?, updated_at=now() WHERE id=?
                        """, attempts, java.sql.Timestamp.from(next), "EMAIL_SEND_FAILED", delivery.id());
            }
        }
    }

    private SimpleMailMessage emailMessage(Delivery delivery) {
        JsonNode decision = delivery.payload().path("decision");
        String verdict = decision.path("final_verdict").asString();
        String repository = decision.path("repository").asString();
        String scanId = decision.path("scan_id").asString();
        String validation = decision.path("validation_status").asString("NOT_RUN");
        var message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(delivery.email());
        message.setSubject("RiskGraph " + verdict + ": " + repository);
        message.setText("""
                RiskGraph flagged %s for %s (%s).

                Validation: %s. Never included here: source, credentials or response bodies —
                review the evidence in the dashboard:
                %s/#/scans/%s
                """.formatted(repository, verdict, verdict, validation, dashboardOrigin, scanId));
        return message;
    }

    private void markSent(Long id) {
        db.update("UPDATE notification_deliveries SET status='SENT', delivered_at=now(), updated_at=now() WHERE id=?", id);
    }

    private void markFailed(Long id, String reason) {
        db.update("UPDATE notification_deliveries SET status='FAILED', failed_reason=?, updated_at=now() WHERE id=?",
                reason, id);
    }

    private OutboxEvent mapOutboxEvent(java.sql.ResultSet values, int row) throws java.sql.SQLException {
        return new OutboxEvent(values.getObject("event_id", java.util.UUID.class), values.getLong("scan_id"),
                mapper.readTree(values.getString("payload")));
    }

    private Delivery mapDelivery(java.sql.ResultSet values, int row) throws java.sql.SQLException {
        return new Delivery(values.getLong("id"), values.getInt("attempts"), values.getLong("user_id"),
                values.getString("channel"), mapper.readTree(values.getString("payload")),
                values.getString("username"), values.getString("email"));
    }

    private record OutboxEvent(java.util.UUID eventId, Long scanId, JsonNode payload) { }
    private record Delivery(Long id, int attempts, Long userId, String channel, JsonNode payload,
            String username, String email) { }

    private static final class RateLimiter {
        private final int maxPerWindow;
        private final Duration window;
        private final Map<Long, Deque<Instant>> sent = new ConcurrentHashMap<>();

        RateLimiter(int maxPerWindow, Duration window) {
            this.maxPerWindow = maxPerWindow;
            this.window = window;
        }

        synchronized boolean allow(Long userId) {
            Deque<Instant> history = sent.computeIfAbsent(userId, id -> new ArrayDeque<>());
            Instant cutoff = Instant.now().minus(window);
            while (!history.isEmpty() && history.peekFirst().isBefore(cutoff)) history.pollFirst();
            if (history.size() >= maxPerWindow) return false;
            history.addLast(Instant.now());
            return true;
        }
    }
}
