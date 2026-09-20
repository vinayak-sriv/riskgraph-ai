package ai.riskgraph.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class NotificationServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void listReturnsOneExtraPageMarkerAndStopsAtLimit() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(call -> {
            RowMapper<NotificationService.Notification> mapper = call.getArgument(1);
            var row = mock(java.sql.ResultSet.class);
            when(row.getLong("id")).thenReturn(9L, 8L);
            when(row.getString("repository")).thenReturn("demo/riskgraph-sample");
            when(row.getString("verdict")).thenReturn("BLOCK");
            when(row.getObject("risk_before")).thenReturn(10);
            when(row.getObject("risk_after")).thenReturn(70);
            when(row.getString("scan_id")).thenReturn("scan-1");
            when(row.getString("validation_status")).thenReturn("CONFIRMED");
            when(row.getString("confidence")).thenReturn("HIGH");
            when(row.getTimestamp("created_at")).thenReturn(Timestamp.from(java.time.Instant.EPOCH));
            when(row.getTimestamp("read_at")).thenReturn(null);
            return java.util.List.of(mapper.mapRow(row, 0), mapper.mapRow(row, 1));
        });

        var service = new NotificationService(db);
        NotificationService.Page page = service.list(1L, null, 1);

        assertThat(page.items()).hasSize(1);
        assertThat(page.nextCursor()).isEqualTo(9L);

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(db).query(anyString(), any(RowMapper.class), args.capture());
        // userId, channel, before (Long.MAX_VALUE sentinel for "no cursor"), limit+1
        assertThat(args.getValue()).containsExactly(1L, "IN_APP", Long.MAX_VALUE, 2);
    }

    @Test
    void markReadIsOwnershipScoped() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.update(anyString(), any(Object[].class))).thenReturn(1);
        var service = new NotificationService(db);

        assertThat(service.markRead(5L, 1L)).isTrue();

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(db).update(anyString(), args.capture());
        assertThat(args.getValue()).containsExactly(5L, 1L);
    }

    @Test
    void preferencesFallBackToDefaultsWhenNoRowExists() {
        // A mock JdbcTemplate no-ops the void query(..., RowCallbackHandler, ...)
        // overload by default, which is exactly "no preference rows exist".
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Boolean.class), any(Object[].class)))
                .thenReturn(false);
        var service = new NotificationService(db);

        NotificationService.Preferences prefs = service.preferences(1L);

        assertThat(prefs.unsubscribedAll()).isFalse();
        assertThat(prefs.channels()).containsExactly(
                new NotificationService.ChannelPreference("IN_APP", true, "REVIEW"),
                new NotificationService.ChannelPreference("EMAIL", false, "REVIEW"));
    }
}
