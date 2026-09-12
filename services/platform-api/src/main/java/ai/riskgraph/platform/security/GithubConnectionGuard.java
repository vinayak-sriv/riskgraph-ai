package ai.riskgraph.platform.security;

import ai.riskgraph.platform.service.PipelineException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class GithubConnectionGuard {
    private final AccountService accounts;
    public GithubConnectionGuard(AccountService accounts) { this.accounts = accounts; }

    public void requireLinked() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
            || !accounts.isGithubLinked(authentication.getName())) {
            throw new PipelineException("GITHUB_CONNECTION_REQUIRED", 403,
                "Connect your GitHub account to access this service");
        }
    }
}
