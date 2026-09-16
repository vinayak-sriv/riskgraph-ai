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
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public String resolve(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        if (!trustedProxyAddresses.contains(peer)) return peer;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null) return peer;
        String first = forwarded.split(",", 2)[0].strip();
        return isAddressLiteral(first) ? first : peer;
    }

    private boolean isAddressLiteral(String value) {
        if (value.isEmpty() || value.length() > 45) return false;
        if (value.contains(":")) {
            return value.matches("[0-9a-fA-F:]+") && !value.contains(":::");
        }
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) return false;
        try {
            return Arrays.stream(octets).allMatch(octet -> !octet.isEmpty()
                    && octet.length() <= 3 && Integer.parseInt(octet) <= 255);
        } catch (NumberFormatException error) {
            return false;
        }
    }
}
