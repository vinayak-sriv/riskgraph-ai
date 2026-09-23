package ai.riskgraph.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.json.JsonMapper;

class NotificationQueueStoreTest {
    @Test
    @SuppressWarnings("unchecked")
    void claimingWorkMarksItProcessingBeforeReturning() throws Exception {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(call -> {
            RowMapper<NotificationQueueStore.Delivery> mapper = call.getArgument(1);
            ResultSet row = mock(ResultSet.class);
            when(row.getLong("id")).thenReturn(4L);
            when(row.getLong("user_id")).thenReturn(9L);
            when(row.getLong("scan_id")).thenReturn(7L);
            when(row.getString("channel")).thenReturn("EMAIL");
            when(row.getString("payload")).thenReturn("{\"decision\":{}}");
            when(row.getString("email")).thenReturn("dev@example.com");
            return List.of(mapper.mapRow(row, 0));
        });
        var store = new NotificationQueueStore(db, JsonMapper.builder().build());

        List<NotificationQueueStore.Delivery> claimed = store.claimDueDeliveries();

        assertThat(claimed).singleElement().extracting(NotificationQueueStore.Delivery::id).isEqualTo(4L);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(db).query(sql.capture(), any(RowMapper.class), args.capture());
        assertThat(sql.getValue()).contains("FOR UPDATE OF d SKIP LOCKED", "UPDATE notification_deliveries");
        assertThat(args.getValue()).containsExactly(20);
        verify(db, never()).update(anyString(), any(Object[].class));
    }
}
