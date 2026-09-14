package ai.riskgraph.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class LoginAttemptServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-13T00:00:00Z"),
            ZoneOffset.UTC);

    @Test
    void oneNetworkUsingManyUsernamesIsThrottled() {
        LoginAttemptService service = new LoginAttemptService(CLOCK, 3, 5, 300, 60);

        for (int index = 0; index < 5; index++) {
            service.failed(request("user" + index, "192.0.2.1"));
        }

        assertThat(service.blocked(request("new-user", "192.0.2.1"))).isTrue();
        assertThat(service.blocked(request("new-user", "192.0.2.2"))).isFalse();
    }

    @Test
    void capacitySaturationDoesNotBlockAnUnrelatedLogin() {
        LoginAttemptService service = new LoginAttemptService(CLOCK, 3, 100_000, 300, 60);
        for (int index = 0; index < 10_000; index++) {
            service.failed(request("attacker" + index, "198.51.100.10"));
        }

        MockHttpServletRequest legitimate = request("legitimate", "203.0.113.20");
        assertThat(service.blocked(legitimate)).isFalse();
        service.failed(legitimate);
        assertThat(service.blocked(legitimate)).isFalse();
        service.failed(legitimate);
        service.failed(legitimate);
        assertThat(service.blocked(legitimate)).isTrue();
    }

    @Test
    void capacityEvictionPreservesActiveAccountLockouts() {
        LoginAttemptService service = new LoginAttemptService(CLOCK, 3, 100_000, 300, 60);
        MockHttpServletRequest target = request("target", "198.51.100.20");
        for (int index = 0; index < 3; index++) service.failed(target);

        for (int index = 0; index < 12_000; index++) {
            service.failed(request("distributed" + index, "198.51.100.21"));
        }

        assertThat(service.blocked(target)).isTrue();
    }

    private MockHttpServletRequest request(String username, String address) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("username", username);
        request.setRemoteAddr(address);
        return request;
    }
}
