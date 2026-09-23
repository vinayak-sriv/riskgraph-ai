package ai.riskgraph.platform.security;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import ai.riskgraph.platform.service.PipelineException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
public class AccountController {
    private final AccountService accounts;
    private final GithubOAuthSettings github;
    private final GithubConnectionGuard githubGuard;
    public AccountController(AccountService accounts, GithubOAuthSettings github,
            GithubConnectionGuard githubGuard) {
        this.accounts = accounts; this.github = github; this.githubGuard = githubGuard;
    }

    @GetMapping("/auth/csrf") public Map<String, String> csrf(CsrfToken csrf) {
        return Map.of("headerName", csrf.getHeaderName(), "token", csrf.getToken());
    }
    @GetMapping("/auth/session") public Map<String, Object> session(Principal principal, HttpServletRequest request) {
        var result = new LinkedHashMap<String, Object>();
        result.put("authenticated", principal != null);
        if (principal != null) result.put("user", accounts.profile(principal.getName()));
        HttpSession session = request.getSession(false);
        String error = session == null ? null : (String) session.getAttribute(GithubAuthenticationSuccessHandler.LAST_ERROR);
        if (session != null) session.removeAttribute(GithubAuthenticationSuccessHandler.LAST_ERROR);
        result.put("github", error == null
            ? accounts.githubConnection(principal == null ? null : principal.getName(), github.configured())
            : new AccountService.GithubConnection(AccountService.GithubStatus.ERROR, null, List.of(), error));
        result.put("github_connection_required", githubGuard.required());
        return result;
    }
    @GetMapping("/auth/github/connect") public void connectGithub(Principal principal, HttpServletRequest request,
        HttpServletResponse response) throws IOException {
        if (!github.configured())
            throw new PipelineException("GITHUB_OAUTH_NOT_CONFIGURED", 503,
                "GitHub authorization is not configured on this RiskGraph installation");
        if (principal != null) request.getSession().setAttribute(GithubAuthenticationSuccessHandler.LINK_USERNAME, principal.getName());
        response.sendRedirect("/oauth2/authorization/github");
    }
    @GetMapping("/admin/users") public List<AccountService.Profile> users() { return accounts.list(); }
    @PostMapping("/admin/users") public AccountService.Profile create(@RequestBody NewAccount request) {
        return accounts.create(request.username(), request.name(), request.role(), request.password());
    }
    @PutMapping("/admin/users/{username}/email") public void setVerifiedEmail(
        @PathVariable String username, @Valid @RequestBody NotificationEmail request) {
        accounts.setVerifiedEmail(username, request.email(), request.verified());
    }
    public record NewAccount(String username, String name, AccountService.Role role, String password) {}
    public record NotificationEmail(@NotBlank @Email String email, boolean verified) {}
}
