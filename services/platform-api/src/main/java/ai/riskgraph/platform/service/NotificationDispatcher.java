package ai.riskgraph.platform.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

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
    private final NotificationQueueStore queue;
    private final NotificationRecipientResolver recipients;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final String fromAddress;
    private final String dashboardOrigin;
    // ponytail: per-instance in-memory limiter; move to a shared store (e.g. Redis)
    // if this ever runs as more than one dispatcher instance.
    private final RateLimiter rateLimiter = new RateLimiter(20, Duration.ofMinutes(1));

    public NotificationDispatcher(NotificationQueueStore queue,
            NotificationRecipientResolver recipients, ObjectProvider<JavaMailSender> mailSender,
            @Value("${riskgraph.notifications.email.from:notifications@riskgraph.local}") String fromAddress,
            @Value("${riskgraph.web.allowed-origin}") String dashboardOrigin) {
        this.queue = queue;
        this.recipients = recipients;
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.dashboardOrigin = dashboardOrigin.replaceAll("/+$", "");
    }

    @Scheduled(fixedDelayString = "${riskgraph.notifications.poll-interval-ms:5000}")
    public void poll() {
        queue.fanOutDue(recipients);
        attemptDueDeliveries();
    }

    void attemptDueDeliveries() {
        for (NotificationQueueStore.Delivery delivery : queue.claimDueDeliveries()) attempt(delivery);
    }

    private void attempt(NotificationQueueStore.Delivery delivery) {
        JsonNode decision = delivery.payload().path("decision");
        if (!recipients.isEligible(delivery.scanId(), decision.path("repository").asString(),
                decision.path("final_verdict").asString(), delivery.userId(), delivery.channel())) {
            queue.markFailed(delivery.id(), "RECIPIENT_NO_LONGER_ELIGIBLE");
            return;
        }
        if (!rateLimiter.allow(delivery.userId())) {
            queue.releaseRateLimited(delivery.id());
            return;
        }
        if ("IN_APP".equals(delivery.channel())) {
            queue.markSent(delivery.id());
            return;
        }
        if (delivery.email() == null || delivery.email().isBlank()) {
            queue.markFailed(delivery.id(), "NO_EMAIL_ADDRESS");
            return;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            queue.markFailed(delivery.id(), "EMAIL_NOT_CONFIGURED");
            return;
        }
        try {
            sender.send(emailMessage(delivery));
            queue.markSent(delivery.id());
        } catch (MailException error) {
            int attempts = delivery.attempts() + 1;
            LOG.warn("notification_email_failed delivery_id={} attempts={}", delivery.id(), attempts, error);
            if (attempts >= MAX_ATTEMPTS) {
                queue.markFailed(delivery.id(), "EMAIL_SEND_FAILED");
            } else {
                Instant next = Instant.now().plusSeconds((long) Math.pow(2, attempts) * 60);
                queue.reschedule(delivery.id(), attempts, next, "EMAIL_SEND_FAILED");
            }
        }
    }

    private SimpleMailMessage emailMessage(NotificationQueueStore.Delivery delivery) {
        JsonNode decision = delivery.payload().path("decision");
        String verdict = decision.path("final_verdict").asString();
        String repository = decision.path("repository").asString();
        String scanId = decision.path("scan_id").asString();
        String validation = decision.path("validation_status").asString("NOT_RUN");
        String certainty = "CONFIRMED".equals(validation) ? "Docker-confirmed" : "Possible";
        int before = decision.path("risk_before").asInt();
        int after = decision.path("risk_after").asInt();
        int delta = decision.path("risk_delta").asInt(after - before);
        String confidence = decision.path("confidence").asString("LOW");
        String head = decision.path("new_commit").asString();
        String evidenceUrl = decision.at("/pull_request/url").asString();
        if (evidenceUrl.isBlank()) evidenceUrl = dashboardOrigin + "/#/scans/" + scanId;
        var message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(delivery.email());
        message.setSubject("RiskGraph " + verdict + ": " + repository);
        message.setText("""
                %s RiskGraph %s decision for %s.

                Risk: %d -> %d (delta %+d). Extraction confidence: %s.
                Validation: %s. Head commit: %s.
                Never included here: source, credentials or response bodies —
                review the protected evidence:
                %s
                """.formatted(certainty, verdict, repository, before, after, delta, confidence,
                        validation, head, evidenceUrl));
        return message;
    }

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
