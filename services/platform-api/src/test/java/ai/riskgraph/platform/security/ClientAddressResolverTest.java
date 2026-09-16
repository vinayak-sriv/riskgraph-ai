package ai.riskgraph.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientAddressResolverTest {
    @Test
    void ignoresForwardedAddressFromAnUntrustedPeer() {
        var request = request("192.0.2.10", "203.0.113.7");
        assertThat(new ClientAddressResolver("").resolve(request)).isEqualTo("192.0.2.10");
    }

    @Test
    void acceptsTheFirstAddressOnlyFromAnExplicitlyTrustedProxy() {
        var request = request("192.0.2.10", "203.0.113.7, 198.51.100.2");
        assertThat(new ClientAddressResolver("192.0.2.10").resolve(request))
                .isEqualTo("203.0.113.7");
    }

    @Test
    void rejectsForwardedHostnamesEvenFromATrustedProxy() {
        var request = request("192.0.2.10", "attacker.example");
        assertThat(new ClientAddressResolver("192.0.2.10").resolve(request))
                .isEqualTo("192.0.2.10");
        assertThat(new ClientAddressResolver("192.0.2.10")
                .resolve(request("192.0.2.10", "999.0.0.1")))
                .isEqualTo("192.0.2.10");
    }

    private MockHttpServletRequest request(String peer, String forwarded) {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(peer);
        request.addHeader("X-Forwarded-For", forwarded);
        return request;
    }
}
