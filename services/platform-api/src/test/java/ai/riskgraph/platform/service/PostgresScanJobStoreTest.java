package ai.riskgraph.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class PostgresScanJobStoreTest {
    @Test
    @SuppressWarnings("unchecked")
    void conflictingSubmissionReusesWinnerWithoutAnAbortedTransaction() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        Instant now = Instant.now();
        var winner = new ScanJobStore.ScanJob(UUID.randomUUID(), "key", "analyst", "/repo",
                "old", "new", ScanJobStore.State.QUEUED, "QUEUED", 0, null, null, now, now);
        when(db.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(), List.of(winner));
        when(db.update(anyString(), any(Object[].class))).thenReturn(0);

        assertThat(new PostgresScanJobStore(db).createOrReuse(
                "key", "analyst", "/repo", "old", "new")).isEqualTo(winner);
        verify(db).update(contains("DO NOTHING"), any(Object[].class));
    }
}
