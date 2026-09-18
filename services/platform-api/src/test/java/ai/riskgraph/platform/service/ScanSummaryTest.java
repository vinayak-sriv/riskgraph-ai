package ai.riskgraph.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import tools.jackson.databind.json.JsonMapper;

class ScanSummaryTest {
    @Test
    void postgresLoadsOnlySummaryColumnsInOneBatch() throws Exception {
        JdbcTemplate db = mock(JdbcTemplate.class);
        doAnswer(call -> {
            String sql = call.getArgument(0);
            assertThat(sql).contains("risk_after-risk_before", "final_verdict", "IN (?,?)")
                    .doesNotContain("result_json");
            RowCallbackHandler handler = call.getArgument(1);
            var row = mock(java.sql.ResultSet.class);
            org.mockito.Mockito.when(row.getString("external_id")).thenReturn("one");
            org.mockito.Mockito.when(row.getObject("risk_delta", Integer.class)).thenReturn(69);
            org.mockito.Mockito.when(row.getString("final_verdict")).thenReturn("BLOCK");
            handler.processRow(row);
            return null;
        }).when(db).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));
        var store = new PostgresScanStore(db, JsonMapper.builder().build(), mock(NotificationOutboxWriter.class));
        assertThat(store.summaries(List.of("one", "two", "one")))
                .containsOnlyKeys("one").containsEntry("one", new ScanStore.Summary(69, "BLOCK"));
        org.mockito.Mockito.verify(db).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));
    }

    @Test
    void emptyPageDoesNotQueryDatabase() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        var store = new PostgresScanStore(db, JsonMapper.builder().build(), mock(NotificationOutboxWriter.class));
        assertThat(store.summaries(List.of())).isEmpty();
        verifyNoInteractions(db);
    }

    @Test
    void memorySummariesReturnOnlyRequestedExistingScans() {
        var mapper = JsonMapper.builder().build();
        var store = new MemoryScanStore();
        var result = mapper.createObjectNode().put("scan_id", "one").put("final_verdict", "REVIEW");
        result.putObject("risk_result").put("risk_delta", 12);
        store.save(result);
        assertThat(store.summaries(List.of("one", "absent")))
                .containsExactlyEntriesOf(java.util.Map.of("one", new ScanStore.Summary(12, "REVIEW")));
    }
}
