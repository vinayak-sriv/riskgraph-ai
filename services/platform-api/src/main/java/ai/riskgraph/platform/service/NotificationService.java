package ai.riskgraph.platform.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Read/manage side of the notification manager: a signed-in user's own
 * in-app deliveries, and their channel preferences and unsubscribe state.
 * Writing/dispatching deliveries is NotificationOutboxWriter/Dispatcher; this
 * is deliberately a separate, read-oriented service. Postgres-only, like the
 * rest of the notification subsystem (no in-memory equivalent for "local").
 */
@Component
@Profile("!local")
public class NotificationService {
    private static final String IN_APP = "IN_APP";
    private static final List<String> CHANNELS = List.of("IN_APP", "EMAIL");

    private final JdbcTemplate db;

    public NotificationService(JdbcTemplate db) {
        this.db = db;
    }

    public Long userId(String username) {
        return db.queryForObject("SELECT id FROM users WHERE username=?", Long.class, username);
    }

    public record Notification(Long id, String repository, String verdict, Integer riskBefore,
            Integer riskAfter, String scanId, String validationStatus, String confidence,
            Integer pullRequestNumber, String pullRequestUrl, Instant createdAt, Instant readAt) { }

    public record Page(List<Notification> items, Long nextCursor) { }

    public Page list(Long userId, Long cursor, int requestedLimit) {
        int limit = Math.max(1, Math.min(100, requestedLimit));
        // No cursor (first page) uses a sentinel above any real id, avoiding a
        // bound-null comparison — same idiom PostgresScanJobStore uses for its
        // own keyset cursor.
        long before = cursor == null ? Long.MAX_VALUE : cursor;
        List<Notification> rows = db.query("""
                SELECT d.id, d.created_at, d.read_at,
                       o.payload->'decision'->>'repository' AS repository,
                       o.payload->'decision'->>'final_verdict' AS verdict,
                       (o.payload->'decision'->>'risk_before')::int AS risk_before,
                       (o.payload->'decision'->>'risk_after')::int AS risk_after,
                       o.payload->'decision'->>'scan_id' AS scan_id,
                       o.payload->'decision'->>'validation_status' AS validation_status,
                       o.payload->'decision'->>'confidence' AS confidence,
                       (o.payload->'decision'->'pull_request'->>'number')::int AS pr_number,
                       o.payload->'decision'->'pull_request'->>'url' AS pr_url
                FROM notification_deliveries d
                JOIN notification_outbox o ON o.event_id = d.event_id
                WHERE d.user_id = ? AND d.channel = ? AND d.status='SENT' AND d.id < ?
                ORDER BY d.id DESC
                LIMIT ?
                """, (row, i) -> new Notification(row.getLong("id"), row.getString("repository"),
                        row.getString("verdict"), (Integer) row.getObject("risk_before"),
                        (Integer) row.getObject("risk_after"), row.getString("scan_id"),
                        row.getString("validation_status"), row.getString("confidence"),
                        (Integer) row.getObject("pr_number"), row.getString("pr_url"),
                        row.getTimestamp("created_at").toInstant(),
                        row.getTimestamp("read_at") == null ? null : row.getTimestamp("read_at").toInstant()),
                userId, IN_APP, before, limit + 1);
        boolean more = rows.size() > limit;
        List<Notification> page = more ? rows.subList(0, limit) : rows;
        return new Page(List.copyOf(page), more ? page.getLast().id() : null);
    }

    public int unreadCount(Long userId) {
        Integer count = db.queryForObject(
                "SELECT count(*) FROM notification_deliveries WHERE user_id=? AND channel=? AND status='SENT' AND read_at IS NULL",
                Integer.class, userId, IN_APP);
        return count == null ? 0 : count;
    }

    /** Returns false if no matching, owned, unread row existed. */
    public boolean markRead(Long id, Long userId) {
        return db.update("""
                UPDATE notification_deliveries SET read_at = now()
                WHERE id = ? AND user_id = ? AND channel = ?
                  AND status = 'SENT' AND read_at IS NULL
                """, id, userId, IN_APP) > 0;
    }

    public void markAllRead(Long userId) {
        db.update("""
                UPDATE notification_deliveries SET read_at = now()
                WHERE user_id = ? AND channel = ? AND status = 'SENT' AND read_at IS NULL
                """, userId, IN_APP);
    }

    public record ChannelPreference(String channel, boolean enabled, String minSeverity) { }

    public record Preferences(List<ChannelPreference> channels, boolean unsubscribedAll,
            List<String> unsubscribedRepositories) { }

    public Preferences preferences(Long userId) {
        Map<String, ChannelPreference> byChannel = new LinkedHashMap<>();
        // IN_APP defaults on, EMAIL defaults off, per NotificationRecipientResolver's own default.
        byChannel.put("IN_APP", new ChannelPreference("IN_APP", true, "REVIEW"));
        byChannel.put("EMAIL", new ChannelPreference("EMAIL", false, "REVIEW"));
        // An expression lambda here (row -> byChannel.put(...)) is ambiguous between
        // JdbcTemplate's ResultSetExtractor and RowCallbackHandler overloads, since
        // Map.put returns a value; a block body with no return statement is
        // void-compatible only, which resolves it to RowCallbackHandler.
        db.query("SELECT channel, enabled, min_severity FROM notification_preferences WHERE user_id=?",
                (java.sql.ResultSet row) -> {
                    byChannel.put(row.getString("channel"), new ChannelPreference(
                            row.getString("channel"), row.getBoolean("enabled"), row.getString("min_severity")));
                },
                userId);
        Boolean unsubscribedAll = db.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM notification_unsubscribes WHERE user_id=? AND scope='*')",
                Boolean.class, userId);
        List<String> repositoryScopes = db.queryForList(
                "SELECT scope FROM notification_unsubscribes WHERE user_id=? AND scope<>'*' ORDER BY scope",
                String.class, userId);
        return new Preferences(List.copyOf(byChannel.values()), Boolean.TRUE.equals(unsubscribedAll),
                List.copyOf(repositoryScopes));
    }

    public void setPreference(Long userId, String channel, boolean enabled, String minSeverity) {
        if (!CHANNELS.contains(channel))
            throw new PipelineException("INVALID_CHANNEL", 400, "Unknown notification channel: " + channel);
        db.update("""
                INSERT INTO notification_preferences(user_id, channel, enabled, min_severity, updated_at)
                VALUES (?, ?, ?, ?, now())
                ON CONFLICT (user_id, channel)
                DO UPDATE SET enabled = EXCLUDED.enabled, min_severity = EXCLUDED.min_severity, updated_at = now()
                """, userId, channel, enabled, minSeverity);
    }

    public void setUnsubscribedAll(Long userId, boolean unsubscribed) {
        if (unsubscribed) {
            db.update("INSERT INTO notification_unsubscribes(user_id, scope) VALUES (?, '*') ON CONFLICT DO NOTHING",
                    userId);
        } else {
            db.update("DELETE FROM notification_unsubscribes WHERE user_id=? AND scope='*'", userId);
        }
    }

    public void setRepositoryUnsubscribed(Long userId, String repository, boolean unsubscribed) {
        if (repository == null || repository.isBlank() || repository.length() > 255
                || "*".equals(repository))
            throw new PipelineException("INVALID_REPOSITORY_SCOPE", 400,
                    "Repository notification scope is invalid");
        if (unsubscribed) {
            db.update("INSERT INTO notification_unsubscribes(user_id, scope) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    userId, repository);
        } else {
            db.update("DELETE FROM notification_unsubscribes WHERE user_id=? AND scope=?", userId, repository);
        }
    }
}
