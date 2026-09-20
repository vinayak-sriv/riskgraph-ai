package ai.riskgraph.platform.security;

import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GithubOAuthSettings {
    private static final Logger LOG = LoggerFactory.getLogger(GithubOAuthSettings.class);
    private static final String DISABLED = "riskgraph-oauth-disabled";
    // GitHub client ids are "Iv1."/"Iv23li"-prefixed; secrets are 40 hex characters.
    private static final Pattern CLIENT_ID = Pattern.compile("[A-Za-z0-9._-]{8,64}");
    private static final Pattern CLIENT_SECRET = Pattern.compile("[0-9a-fA-F]{40}");
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
        boolean ready = usable(clientId, CLIENT_ID) && usable(clientSecret, CLIENT_SECRET);
        if (!ready && !DISABLED.equals(clientSecret)) {
            // Without this, a malformed secret silently advertises a working
            // "Connect GitHub" button that fails at the token exchange.
            LOG.warn("github_oauth operation=configure result=skipped "
                    + "reason=client-id-or-secret-not-well-formed");
        }
        return ready;
    }

    public String accountUrl() { return dashboardOrigin + "/#account"; }

    private static boolean usable(String value, Pattern shape) {
        return value != null && !value.isBlank() && !DISABLED.equals(value)
            && !value.startsWith("replace_with_") && shape.matcher(value).matches();
    }
}
