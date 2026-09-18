package ai.riskgraph.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class NotificationRecipientResolverTest {
    @Test
    @SuppressWarnings("unchecked")
    void queriesLiveMembershipAndAppliesSeverityFilterAndDefaults() throws Exception {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(call -> {
            RowMapper<NotificationRecipientResolver.Recipient> mapper = call.getArgument(1);
            var row = mock(java.sql.ResultSet.class);
            when(row.getLong("user_id")).thenReturn(42L);
            when(row.getString("channel")).thenReturn("IN_APP");
            return List.of(mapper.mapRow(row, 0));
        });

        var resolver = new NotificationRecipientResolver(db);
        List<NotificationRecipientResolver.Recipient> recipients =
                resolver.resolve(7L, "demo/riskgraph-sample", "BLOCK");

        assertThat(recipients).containsExactly(new NotificationRecipientResolver.Recipient(42L, "IN_APP"));

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(db).query(anyString(), any(RowMapper.class), args.capture());
        assertThat(args.getValue()).containsExactly(7L, "BLOCK", "demo/riskgraph-sample");
    }
}
