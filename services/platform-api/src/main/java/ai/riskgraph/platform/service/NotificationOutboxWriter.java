package ai.riskgraph.platform.service;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Writes a notification-event row into the transactional outbox, in the same
 * database transaction as the scan result that decided it. REVIEW and BLOCK
 * only, per AGENTS.md Section 8 item 2; ALLOW is not notification-worthy.
 */
@Component @Profile("!local")
public class NotificationOutboxWriter {
    private static final Set<String> NOTIFIABLE = Set.of("REVIEW", "BLOCK");

    private final JdbcTemplate db;
    private final ObjectMapper mapper;
    private final ContractValidator contracts;

    public NotificationOutboxWriter(JdbcTemplate db, ObjectMapper mapper, ContractValidator contracts) {
        this.db = db;
        this.mapper = mapper;
        this.contracts = contracts;
    }

    /** Call inside the same transaction that persists the scan's final decision. */
    public void write(Long scanId, JsonNode result) {
        String verdict = result.path("final_verdict").asString();
        if (!NOTIFIABLE.contains(verdict)) return;

        String scanExternalId = result.path("scan_id").asString();
        JsonNode provenance = result.path("provenance");
        String occurredAt = Instant.now().toString();

        ObjectNode decision = mapper.createObjectNode();
        decision.put("schema_version", "1.0.0");
        decision.put("scan_id", scanExternalId);
        decision.put("repository", provenance.path("repository_identity").asString());
        decision.put("old_commit", provenance.path("old_commit").asString());
        decision.put("new_commit", provenance.path("new_commit").asString());
        decision.put("final_verdict", verdict);
        decision.put("risk_before", result.at("/risk_result/risk_before").asInt());
        decision.put("risk_after", result.at("/risk_result/risk_after").asInt());
        decision.put("validation_status", result.path("validation_status").asString("NOT_RUN"));
        decision.put("decided_at", occurredAt);
        contracts.validate("notifications/final-decision.schema.json", decision);

        // Stable across redeliveries of the same decision; a re-save with an
        // unchanged verdict is a no-op insert, a verdict change is a new event.
        String dedupKey = scanExternalId + ":" + verdict;
        String eventId = UUID.randomUUID().toString();

        ObjectNode event = mapper.createObjectNode();
        event.put("schema_version", "1.0.0");
        event.put("event_id", eventId);
        event.put("event_type", "final-decision");
        event.put("occurred_at", occurredAt);
        event.put("dedup_key", dedupKey);
        event.set("decision", decision);
        contracts.validate("notifications/notification-event.schema.json", event);

        db.update("""
                INSERT INTO notification_outbox(event_id, scan_id, dedup_key, severity, payload)
                VALUES (?::uuid, ?, ?, ?, ?::jsonb)
                ON CONFLICT (dedup_key) DO NOTHING
                """, eventId, scanId, dedupKey, verdict, event.toString());
    }
}
