package ai.riskgraph.platform.security;

import ai.riskgraph.platform.service.PipelineException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class GithubConnectionGuard {
    private final AccountService accounts;
    private final boolean required;

    public GithubConnectionGuard(AccountService accounts,
            @Value("${RISKGRAPH_REQUIRE_GITHUB_CONNECTION:true}") boolean required) {
        this.accounts = accounts;
        this.required = required;
    }

    public void requireLinked() {
        if (!required) return;
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
            || !accounts.isGithubLinked(authentication.getName())) {
            throw new PipelineException("GITHUB_CONNECTION_REQUIRED", 403,
                "Connect your GitHub account to access this service");
        }
    }

    public boolean required() {
        return required;
    }
}
