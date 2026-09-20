package ai.riskgraph.platform.api;

import ai.riskgraph.platform.service.NotificationService;
import ai.riskgraph.platform.service.PipelineException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

// Entirely Postgres-backed via NotificationService; no in-memory/local variant,
// so this controller doesn't exist under the local profile either.
@RestController
@Profile("!local")
public class NotificationController {
    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping("/notifications")
    public NotificationPage list(@RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int limit, Authentication authentication) {
        Long userId = notifications.userId(authentication.getName());
        NotificationService.Page page = notifications.list(userId, cursor, limit);
        List<NotificationItem> items = page.items().stream().map(NotificationController::toItem).toList();
        return new NotificationPage(items, page.nextCursor(), notifications.unreadCount(userId));
    }

    @PostMapping("/notifications/{id}/read")
    public void markRead(@PathVariable Long id, Authentication authentication) {
        Long userId = notifications.userId(authentication.getName());
        if (!notifications.markRead(id, userId))
            throw new PipelineException("NOTIFICATION_NOT_FOUND", 404,
                    "No unread notification with that id for your account");
    }

    @PostMapping("/notifications/read-all")
    public void markAllRead(Authentication authentication) {
        notifications.markAllRead(notifications.userId(authentication.getName()));
    }

    @GetMapping("/notifications/preferences")
    public PreferencesResponse preferences(Authentication authentication) {
        return toResponse(notifications.preferences(notifications.userId(authentication.getName())));
    }

    @PutMapping("/notifications/preferences")
    public PreferencesResponse setPreference(@Valid @RequestBody PreferenceUpdate update,
            Authentication authentication) {
        Long userId = notifications.userId(authentication.getName());
        notifications.setPreference(userId, update.channel(), update.enabled(), update.min_severity());
        return toResponse(notifications.preferences(userId));
    }

    @PutMapping("/notifications/preferences/unsubscribe-all")
    public PreferencesResponse setUnsubscribedAll(@RequestBody UnsubscribeUpdate update,
            Authentication authentication) {
        Long userId = notifications.userId(authentication.getName());
        notifications.setUnsubscribedAll(userId, update.unsubscribed());
        return toResponse(notifications.preferences(userId));
    }

    private static NotificationItem toItem(NotificationService.Notification n) {
        return new NotificationItem(n.id(), n.repository(), n.verdict(), n.riskBefore(),
                n.riskAfter(), n.scanId(), n.validationStatus(), n.confidence(), n.createdAt(), n.readAt());
    }

    private static PreferencesResponse toResponse(NotificationService.Preferences prefs) {
        return new PreferencesResponse(prefs.channels().stream()
                .map(c -> new ChannelPreferenceItem(c.channel(), c.enabled(), c.minSeverity())).toList(),
                prefs.unsubscribedAll());
    }

    public record NotificationItem(Long id, String repository, String verdict, Integer risk_before,
            Integer risk_after, String scan_id, String validation_status, String confidence,
            Instant created_at, Instant read_at) { }
    public record NotificationPage(List<NotificationItem> items, Long next_cursor, int unread_count) { }
    public record ChannelPreferenceItem(String channel, boolean enabled, String min_severity) { }
    public record PreferencesResponse(List<ChannelPreferenceItem> channels, boolean unsubscribed_all) { }
    public record PreferenceUpdate(
            @NotBlank @Pattern(regexp = "IN_APP|EMAIL") String channel,
            boolean enabled,
            @NotBlank @Pattern(regexp = "REVIEW|BLOCK") String min_severity) { }
    public record UnsubscribeUpdate(boolean unsubscribed) { }
}
