package ai.riskgraph.platform.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import tools.jackson.databind.json.JsonMapper;

class NotificationDispatcherTest {
    private final NotificationQueueStore queue = mock(NotificationQueueStore.class);
    private final NotificationRecipientResolver recipients = mock(NotificationRecipientResolver.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<JavaMailSender> mailSender = mock(ObjectProvider.class);
    private final NotificationDispatcher dispatcher = new NotificationDispatcher(queue,
            recipients, mailSender,
            "notifications@riskgraph.local", "http://localhost:5173");

    @Test
    void inAppDeliveryIsMarkedSentWithoutNeedingMail() throws Exception {
        stubDueDelivery("IN_APP", null);
        dispatcher.attemptDueDeliveries();
        verify(queue).markSent(1L);
        verifyNoInteractions(mailSender);
    }

    @Test
    void emailDeliveryWithNoAddressFailsImmediatelyWithoutRetrying() throws Exception {
        stubDueDelivery("EMAIL", null);
        dispatcher.attemptDueDeliveries();
        verify(queue).markFailed(1L, "NO_EMAIL_ADDRESS");
        verifyNoInteractions(mailSender);
    }

    @Test
    void emailDeliveryWithNoMailSenderConfiguredFails() throws Exception {
        when(mailSender.getIfAvailable()).thenReturn(null);
        stubDueDelivery("EMAIL", "dev@example.com");
        dispatcher.attemptDueDeliveries();
        verify(queue).markFailed(1L, "EMAIL_NOT_CONFIGURED");
    }

    @Test
    void revokedRecipientIsRejectedBeforeAnyDelivery() throws Exception {
        stubDueDelivery("EMAIL", "dev@example.com");
        when(recipients.isEligible(7L, "demo/repo", "BLOCK", 9L, "EMAIL")).thenReturn(false);

        dispatcher.attemptDueDeliveries();

        verify(queue).markFailed(1L, "RECIPIENT_NO_LONGER_ELIGIBLE");
        verifyNoInteractions(mailSender);
    }

    @Test
    void successfulEmailContainsDecisionSummaryButNoSensitivePayload() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(mailSender.getIfAvailable()).thenReturn(sender);
        stubDueDelivery("EMAIL", "dev@example.com");

        dispatcher.attemptDueDeliveries();

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        verify(queue).markSent(1L);
        assertThat(message.getValue().getText())
                .contains("Possible RiskGraph BLOCK", "demo/repo", "scan-1")
                .doesNotContain("TOP_SECRET", "raw source");
    }

    @Test
    void providerOutageReschedulesWithBoundedRetryMetadata() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(mailSender.getIfAvailable()).thenReturn(sender);
        doThrow(new MailSendException("provider outage"))
                .when(sender).send(any(SimpleMailMessage.class));
        stubDueDelivery("EMAIL", "dev@example.com");

        dispatcher.attemptDueDeliveries();

        verify(queue).reschedule(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(1), any(Instant.class),
                org.mockito.ArgumentMatchers.eq("EMAIL_SEND_FAILED"));
    }

    private void stubDueDelivery(String channel, String email) throws Exception {
        var payload = JsonMapper.builder().build().readTree("""
                {"decision":{"repository":"demo/repo","final_verdict":"BLOCK",
                 "scan_id":"scan-1","validation_status":"NOT_RUN",
                 "risk_before":10,"risk_after":90,"risk_delta":80,"confidence":"HIGH",
                 "raw_source":"raw source TOP_SECRET"}}""");
        when(queue.claimDueDeliveries()).thenReturn(List.of(
                new NotificationQueueStore.Delivery(1L, 0, 9L, channel, 7L, payload, email)));
        when(recipients.isEligible(7L, "demo/repo", "BLOCK", 9L, channel)).thenReturn(true);
    }
}
