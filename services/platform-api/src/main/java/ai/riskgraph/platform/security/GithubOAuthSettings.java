package ai.riskgraph.platform.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GithubOAuthSettings {
    private static final String DISABLED = "riskgraph-oauth-disabled";
    private final String clientId;
    private final String clientSecret;
    private final String dashboardOrigin;

    public GithubOAuthSettings(@Value("${riskgraph.github.client-id:" + DISABLED + "}") String clientId,
        @Value("${riskgraph.github.client-secret:" + DISABLED + "}") String clientSecret,
        @Value("${riskgraph.web.allowed-origin}") String dashboardOrigin) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.dashboardOrigin = dashboardOrigin.replaceAll("/+$", "");
    }

    public boolean configured() {
        return usable(clientId) && usable(clientSecret);
    }

    public String accountUrl() { return dashboardOrigin + "/#account"; }

    private static boolean usable(String value) {
        return value != null && !value.isBlank() && !DISABLED.equals(value)
            && !value.startsWith("replace_with_");
    }
}
