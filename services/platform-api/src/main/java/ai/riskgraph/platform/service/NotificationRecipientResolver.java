package ai.riskgraph.platform.service;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Resolves who should be notified for a scan's decision: enabled users with
 * live scan access (rechecked against scan_memberships at call time, never
 * cached), filtered by per-channel preference, severity threshold and
 * unsubscribes. First slice of the notification manager (AGENTS.md Section 8
 * item 2); dispatch/delivery is a separate, later component.
 */
@Component
@Profile("!local")
public class NotificationRecipientResolver {
    private final JdbcTemplate db;

    public NotificationRecipientResolver(JdbcTemplate db) {
        this.db = db;
    }

    public List<Recipient> resolve(Long scanId, String repository, String verdict) {
        return db.query("""
                SELECT u.id AS user_id, channel.name AS channel
                FROM scan_memberships m
                JOIN users u ON u.id = m.user_id AND u.enabled
                CROSS JOIN (VALUES ('IN_APP'), ('EMAIL')) AS channel(name)
                LEFT JOIN notification_preferences p ON p.user_id = u.id AND p.channel = channel.name
                WHERE m.scan_id = ?
                  AND COALESCE(p.enabled, channel.name = 'IN_APP')
                  AND (COALESCE(p.min_severity, 'REVIEW') <> 'BLOCK' OR ? = 'BLOCK')
                  AND NOT EXISTS (
                      SELECT 1 FROM notification_unsubscribes s
                      WHERE s.user_id = u.id AND s.scope IN ('*', ?))
                """, (values, row) -> new Recipient(values.getLong("user_id"), values.getString("channel")),
                scanId, verdict, repository);
    }

    public boolean isEligible(Long scanId, String repository, String verdict, Long userId, String channel) {
        Boolean eligible = db.queryForObject("""
                SELECT EXISTS(
                    SELECT 1 FROM scan_memberships m
                    JOIN users u ON u.id=m.user_id AND u.enabled
                    LEFT JOIN notification_preferences p ON p.user_id=u.id AND p.channel=?
                    WHERE m.scan_id=? AND u.id=?
                      AND COALESCE(p.enabled, ?='IN_APP')
                      AND (COALESCE(p.min_severity, 'REVIEW') <> 'BLOCK' OR ?='BLOCK')
                      AND NOT EXISTS (
                          SELECT 1 FROM notification_unsubscribes s
                          WHERE s.user_id=u.id AND s.scope IN ('*', ?))
                )
                """, Boolean.class, channel, scanId, userId, channel, verdict, repository);
        return Boolean.TRUE.equals(eligible);
    }

    public record Recipient(Long userId, String channel) { }
}
