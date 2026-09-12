package ai.riskgraph.platform.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

@Component
public class GithubAuthenticationSuccessHandler implements AuthenticationSuccessHandler {
    public static final String LINK_USERNAME = GithubAuthenticationSuccessHandler.class.getName() + ".LINK_USERNAME";
    public static final String LAST_ERROR = GithubAuthenticationSuccessHandler.class.getName() + ".LAST_ERROR";
    private final AccountService accounts;
    private final GithubOAuthSettings settings;
    private final HttpSessionSecurityContextRepository contexts = new HttpSessionSecurityContextRepository();

    public GithubAuthenticationSuccessHandler(AccountService accounts, GithubOAuthSettings settings) {
        this.accounts = accounts;
        this.settings = settings;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
        org.springframework.security.core.Authentication authentication) throws IOException, ServletException {
        try {
            if (!(authentication instanceof OAuth2AuthenticationToken oauth)
                || !"github".equals(oauth.getAuthorizedClientRegistrationId())) {
                throw new IllegalStateException("Unexpected OAuth provider");
            }
            Object rawId = oauth.getPrincipal().getAttribute("id");
            String subject = rawId == null ? "" : String.valueOf(rawId);
            String login = oauth.getPrincipal().getAttribute("login");
            String name = oauth.getPrincipal().getAttribute("name");
            var session = request.getSession();
            String requestedUsername = (String) session.getAttribute(LINK_USERNAME);
            var profile = accounts.connectGithub(subject, login, name, requestedUsername);
            authenticate(profile, request, response);
            session.removeAttribute(LINK_USERNAME);
            session.removeAttribute(LAST_ERROR);
        } catch (RuntimeException exception) {
            var session = request.getSession();
            String requestedUsername = (String) session.getAttribute(LINK_USERNAME);
            session.removeAttribute(LINK_USERNAME);
            session.setAttribute(LAST_ERROR,
                exception instanceof ai.riskgraph.platform.service.PipelineException
                    ? exception.getMessage() : "GitHub authorization could not be completed");
            if (requestedUsername != null) {
                try { authenticate(accounts.profile(requestedUsername), request, response); }
                catch (RuntimeException ignored) { clearAuthentication(request, response); }
            } else clearAuthentication(request, response);
        }
        response.sendRedirect(settings.accountUrl());
    }

    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var session = request.getSession();
        String requestedUsername = (String) session.getAttribute(LINK_USERNAME);
        session.removeAttribute(LINK_USERNAME);
        session.setAttribute(LAST_ERROR, "GitHub authorization was cancelled or could not be completed");
        if (requestedUsername != null) {
            try { authenticate(accounts.profile(requestedUsername), request, response); }
            catch (RuntimeException ignored) { clearAuthentication(request, response); }
        } else clearAuthentication(request, response);
        response.sendRedirect(settings.accountUrl());
    }

    private void authenticate(AccountService.Profile profile, HttpServletRequest request, HttpServletResponse response) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + profile.role().name()));
        var localAuthentication = UsernamePasswordAuthenticationToken.authenticated(profile.username(), null, authorities);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(localAuthentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
    }

    private void clearAuthentication(HttpServletRequest request, HttpServletResponse response) {
        var emptyContext = SecurityContextHolder.createEmptyContext();
        SecurityContextHolder.setContext(emptyContext);
        contexts.saveContext(emptyContext, request, response);
    }
}
