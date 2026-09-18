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
    void ignoresSpoofedPrefixWhenTrustedProxyAppendsTheRealClient() {
        var request = request("192.0.2.10", "203.0.113.7, 198.51.100.2");
        assertThat(new ClientAddressResolver("192.0.2.10").resolve(request))
                .isEqualTo("198.51.100.2");
    }

    @Test
    void skipsOnlyTrustedHopsFromTheRight() {
        var resolver = new ClientAddressResolver("192.0.2.10,198.51.100.2");
        assertThat(resolver.resolve(request("192.0.2.10", "203.0.113.7, 198.51.100.2")))
                .isEqualTo("203.0.113.7");
        assertThat(resolver.resolve(request("192.0.2.10", "198.51.100.2")))
                .isEqualTo("192.0.2.10");
    }

    @Test
    void rejectsMalformedLiteralsWithoutSkippingToAttackerControlledPrefixes() {
        var resolver = new ClientAddressResolver("192.0.2.10");
        for (String invalid : java.util.List.of("-1.2.3.4", "+1.2.3.4", "01.2.3.4",
                "1:2", ":::1", "1:2:3:4:5:6:7:8:9", "fe80::1%eth0", "", "host.example")) {
            assertThat(resolver.resolve(request("192.0.2.10", "203.0.113.7, " + invalid)))
                    .as(invalid).isEqualTo("192.0.2.10");
        }
    }

    @Test
    void normalizesIpv6ForTrustedHopsAndRateLimitKeys() {
        var resolver = new ClientAddressResolver("::1,2001:db8::2");
        assertThat(resolver.resolve(request("0:0:0:0:0:0:0:1", "2001:db8::7, 2001:db8:0:0:0:0:0:2")))
                .isEqualTo("2001:db8:0:0:0:0:0:7");
        assertThat(resolver.resolve(request("::1", "2001:db8:0:0:0:0:0:7")))
                .isEqualTo("2001:db8:0:0:0:0:0:7");
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
