package ai.riskgraph.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ClientAddressResolver {
    private final Set<String> trustedProxyAddresses;

    public ClientAddressResolver(
            @Value("${RISKGRAPH_TRUSTED_PROXY_ADDRESSES:}") String configuredAddresses) {
        this.trustedProxyAddresses = Arrays.stream(configuredAddresses.split(","))
                .map(String::strip)
                .map(ClientAddressResolver::addressLiteral)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    public String resolve(HttpServletRequest request) {
        String peer = addressLiteral(request.getRemoteAddr());
        if (peer == null) return request.getRemoteAddr();
        if (!trustedProxyAddresses.contains(peer)) return peer;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null) return peer;
        String[] hops = forwarded.split(",", -1);
        for (int index = hops.length - 1; index >= 0; index--) {
            String address = addressLiteral(hops[index].strip());
            if (address == null) return peer;
            if (!trustedProxyAddresses.contains(address)) return address;
        }
        return peer;
    }

    private static String addressLiteral(String value) {
        if (value == null || value.isEmpty() || value.length() > 45) return null;
        if (value.contains(":")) {
            // The character allowlist and colon exclude hostnames before parsing;
            // getByName only parses these numeric IPv6 literals, without DNS.
            if (!value.matches("[0-9a-fA-F:.]+")) return null;
            try {
                return java.net.InetAddress.getByName(value).getHostAddress();
            } catch (java.net.UnknownHostException error) {
                return null;
            }
        }
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) return null;
        return Arrays.stream(octets).allMatch(octet -> octet.matches("0|[1-9][0-9]{0,2}")
                && Integer.parseInt(octet) <= 255) ? value : null;
    }
}
