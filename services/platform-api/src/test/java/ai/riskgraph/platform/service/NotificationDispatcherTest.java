package ai.riskgraph.platform.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mail.javamail.JavaMailSender;
import tools.jackson.databind.json.JsonMapper;

class NotificationDispatcherTest {
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final NotificationRecipientResolver recipients = mock(NotificationRecipientResolver.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<JavaMailSender> mailSender = mock(ObjectProvider.class);
    private final NotificationDispatcher dispatcher = new NotificationDispatcher(db,
            JsonMapper.builder().build(), recipients, mailSender,
            "notifications@riskgraph.local", "http://localhost:5173");

    @Test
    void inAppDeliveryIsMarkedSentWithoutNeedingMail() throws Exception {
        stubDueDelivery("IN_APP", null);
        dispatcher.attemptDueDeliveries();
        verify(db).update(contains("status='SENT'"), eq(1L));
        verifyNoInteractions(mailSender);
    }

    @Test
    void emailDeliveryWithNoAddressFailsImmediatelyWithoutRetrying() throws Exception {
        stubDueDelivery("EMAIL", null);
        dispatcher.attemptDueDeliveries();
        verify(db).update(contains("status='FAILED'"), eq("NO_EMAIL_ADDRESS"), eq(1L));
        verifyNoInteractions(mailSender);
    }

    @Test
    void emailDeliveryWithNoMailSenderConfiguredFails() throws Exception {
        when(mailSender.getIfAvailable()).thenReturn(null);
        stubDueDelivery("EMAIL", "dev@example.com");
        dispatcher.attemptDueDeliveries();
        verify(db).update(contains("status='FAILED'"), eq("EMAIL_NOT_CONFIGURED"), eq(1L));
    }

    @SuppressWarnings("unchecked")
    private void stubDueDelivery(String channel, String email) {
        when(db.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(call -> {
            RowMapper<Object> mapper = call.getArgument(1);
            ResultSet row = mock(ResultSet.class);
            when(row.getLong("id")).thenReturn(1L);
            when(row.getInt("attempts")).thenReturn(0);
            when(row.getLong("user_id")).thenReturn(9L);
            when(row.getString("channel")).thenReturn(channel);
            when(row.getString("payload")).thenReturn("""
                    {"decision":{"repository":"demo/repo","final_verdict":"BLOCK",
                     "scan_id":"scan-1","validation_status":"NOT_RUN"}}""");
            when(row.getString("username")).thenReturn("analyst");
            when(row.getString("email")).thenReturn(email);
            return List.of(mapper.mapRow(row, 0));
        });
    }
}
