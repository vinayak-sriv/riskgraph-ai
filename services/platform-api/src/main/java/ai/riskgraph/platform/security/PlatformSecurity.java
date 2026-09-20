package ai.riskgraph.platform.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;

@Configuration
public class PlatformSecurity {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean SecurityFilterChain platformFilterChain(HttpSecurity http, GithubOAuthSettings github,
        GithubAuthenticationSuccessHandler githubSuccess, ClientRegistrationRepository registrations,
        LoginAttemptService loginAttempts, LoginRateLimitFilter loginRateLimitFilter) throws Exception {
        http.cors(Customizer.withDefaults())
            // Default CSRF (XorCsrfTokenRequestAttributeHandler) BREACH-encodes the
            // token on every response, including through .spa()'s SpaCsrfTokenRequestHandler
            // (that variant is for JS reading the XSRF-TOKEN cookie directly). This app hands
            // the raw token to the client as JSON instead (see AccountController#csrf) and
            // expects it echoed back verbatim, so it needs the plain, non-BREACH handler.
            .csrf(csrf -> csrf.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(request -> request.getRequestURI().startsWith(request.getContextPath() + "/demo/")).permitAll()
                .requestMatchers(HttpMethod.GET, "/health", "/actuator/health", "/auth/csrf", "/auth/session",
                    "/auth/github/connect", "/oauth2/authorization/**", "/login/oauth2/code/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/scan-jobs/**").hasAnyRole("DEVELOPER", "ANALYST", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/scan-jobs/**").hasAnyRole("ANALYST", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/analyses/**", "/scans/**").hasAnyRole("DEVELOPER", "ANALYST", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/analyses", "/analyses/**", "/scans").hasAnyRole("ANALYST", "ADMIN")
                // Notifications are personal to every signed-in user, not gated by the
                // analysis-access roles above.
                .requestMatchers("/notifications/**").hasAnyRole("DEVELOPER", "ANALYST", "ADMIN")
                .anyRequest().denyAll())
            .formLogin(form -> form.loginProcessingUrl("/auth/login")
                .successHandler((request, response, authentication) -> {
                    loginAttempts.succeeded(request);
                    response.setContentType("application/json"); response.getWriter().write("{\"authenticated\":true}");
                })
                .failureHandler((request, response, exception) -> {
                    loginAttempts.failed(request);
                    error(response, 401, "INVALID_CREDENTIALS");
                }))
            .logout(logout -> logout.logoutUrl("/auth/logout")
                .logoutSuccessHandler((request, response, authentication) -> {
                    response.setContentType("application/json"); response.getWriter().write("{\"authenticated\":false}");
                }))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) -> error(response, 401, "AUTHENTICATION_REQUIRED"))
                .accessDeniedHandler((request, response, exception) -> error(response, 403,
                    exception instanceof CsrfException ? "INVALID_CSRF_TOKEN" : "ACCESS_DENIED")))
            .requestCache(cache -> cache.disable())
            ;
        if (github.configured()) {
            var resolver = new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
            resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
            http.oauth2Login(oauth -> oauth
                .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(resolver))
                .successHandler(githubSuccess)
                .failureHandler((request, response, exception) -> githubSuccess.onAuthenticationFailure(request, response)));
        }
        return http.build();
    }

    private static void error(jakarta.servlet.http.HttpServletResponse response, int status, String code) throws java.io.IOException {
        response.setStatus(status); response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"Sign in with an authorized account\"}");
    }
}
