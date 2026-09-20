package ai.riskgraph.platform.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Short database transactions for claiming notification work. Network delivery is external. */
@Component
@Profile("!local")
public class NotificationQueueStore {
    private static final int BATCH_SIZE = 20;
    private final JdbcTemplate db;
    private final ObjectMapper mapper;

    public NotificationQueueStore(JdbcTemplate db, ObjectMapper mapper) {
        this.db = db;
        this.mapper = mapper;
    }

    @Transactional
    public void fanOutDue(NotificationRecipientResolver recipients) {
        List<OutboxEvent> due = db.query("""
                SELECT event_id, scan_id, payload::text AS payload FROM notification_outbox
                WHERE status='PENDING' AND next_attempt_at <= now()
                ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED
                """, this::mapOutboxEvent, BATCH_SIZE);
        for (OutboxEvent event : due) {
            JsonNode decision = event.payload().path("decision");
            var targets = recipients.resolve(event.scanId(), decision.path("repository").asString(),
                    decision.path("final_verdict").asString());
            for (var target : targets) {
                db.update("""
                        INSERT INTO notification_deliveries(event_id,user_id,channel)
                        VALUES (?,?,?) ON CONFLICT (event_id,user_id,channel) DO NOTHING
                        """, event.eventId(), target.userId(), target.channel());
            }
            db.update("UPDATE notification_outbox SET status='DELIVERED', updated_at=now() WHERE event_id=?",
                    event.eventId());
        }
    }

    @Transactional
    public List<Delivery> claimDueDeliveries() {
        List<Delivery> due = db.query("""
                SELECT d.id, d.attempts, d.user_id, d.channel, o.scan_id,
                       o.payload::text AS payload, u.email
                FROM notification_deliveries d
                JOIN notification_outbox o ON o.event_id = d.event_id
                JOIN users u ON u.id = d.user_id
                WHERE (d.status='PENDING' OR (d.status='PROCESSING' AND d.next_attempt_at <= now()))
                  AND d.next_attempt_at <= now()
                ORDER BY d.next_attempt_at, d.id LIMIT ? FOR UPDATE SKIP LOCKED
                """, this::mapDelivery, BATCH_SIZE);
        for (Delivery delivery : due) {
            db.update("""
                    UPDATE notification_deliveries
                    SET status='PROCESSING', next_attempt_at=now() + interval '5 minutes', updated_at=now()
                    WHERE id=?
                    """, delivery.id());
        }
        return List.copyOf(due);
    }

    void markSent(Long id) {
        db.update("""
                UPDATE notification_deliveries
                SET status='SENT', delivered_at=now(), failed_reason=NULL, updated_at=now() WHERE id=?
                """, id);
    }

    void markFailed(Long id, String reason) {
        db.update("""
                UPDATE notification_deliveries SET status='FAILED', failed_reason=?, updated_at=now() WHERE id=?
                """, reason, id);
    }

    void reschedule(Long id, int attempts, Instant next, String reason) {
        db.update("""
                UPDATE notification_deliveries
                SET status='PENDING', attempts=?, next_attempt_at=?, failed_reason=?, updated_at=now()
                WHERE id=?
                """, attempts, java.sql.Timestamp.from(next), reason, id);
    }

    void releaseRateLimited(Long id) {
        db.update("""
                UPDATE notification_deliveries
                SET status='PENDING', next_attempt_at=now() + interval '5 seconds', updated_at=now() WHERE id=?
                """, id);
    }

    private OutboxEvent mapOutboxEvent(java.sql.ResultSet row, int index) throws java.sql.SQLException {
        return new OutboxEvent(row.getObject("event_id", UUID.class), row.getLong("scan_id"),
                mapper.readTree(row.getString("payload")));
    }

    private Delivery mapDelivery(java.sql.ResultSet row, int index) throws java.sql.SQLException {
        return new Delivery(row.getLong("id"), row.getInt("attempts"), row.getLong("user_id"),
                row.getString("channel"), row.getLong("scan_id"), mapper.readTree(row.getString("payload")),
                row.getString("email"));
    }

    private record OutboxEvent(UUID eventId, Long scanId, JsonNode payload) { }

    public record Delivery(Long id, int attempts, Long userId, String channel, Long scanId,
            JsonNode payload, String email) { }
}
