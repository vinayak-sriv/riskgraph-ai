package ai.riskgraph.platform.security;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ai.riskgraph.platform.client.GraphRiskClient;
import ai.riskgraph.platform.service.ScanStore;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.profiles.active=local", "RISKGRAPH_BOOTSTRAP_PASSWORD_FILE=", "server.address=127.0.0.1",
        "GITHUB_CLIENT_ID=test-client", "GITHUB_CLIENT_SECRET=test-secret"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlatformSecurityTest {
    @LocalServerPort int port;
    @Autowired AccountService accounts;
    @Autowired ScanStore scans;
    @MockitoBean GraphRiskClient graphRiskClient;
    final JsonMapper mapper = JsonMapper.builder().build();
    final String password = "Only-for-local-tests-42";

    @BeforeAll void users() {
        int githubId = 100;
        for (var role : AccountService.Role.values()) {
            accounts.create(role.name().toLowerCase(), "Test " + role, role, password);
            accounts.connectGithub(String.valueOf(githubId++), role.name().toLowerCase(), "Test " + role,
                role.name().toLowerCase());
        }
        accounts.create("developer2", "Second developer", AccountService.Role.DEVELOPER, password);
        accounts.connectGithub("104", "developer2", "Second developer", "developer2");
        accounts.create("unlinked", "Unlinked analyst", AccountService.Role.ANALYST, password);
    }
    @BeforeEach void graphRiskResponse() {
        when(graphRiskClient.analyze(any())).thenReturn(mapper.createObjectNode()
            .put("verdict", "ALLOW").put("risk_before", 0).put("risk_after", 0));
    }
    HttpClient client() { return HttpClient.newBuilder().cookieHandler(new CookieManager()).build(); }
    HttpResponse<String> send(HttpClient client, String method, String route, String body, String type, boolean csrf) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + route));
        if (csrf) {
            var token = mapper.readTree(send(client, "GET", "/auth/csrf", "", "application/json", false).body());
            request.header(token.path("headerName").asString(), token.path("token").asString());
        }
        request.header("Content-Type", type).method(method, HttpRequest.BodyPublishers.ofString(body));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    HttpClient login(String user) throws Exception {
        var client = client();
        assertThat(send(client, "POST", "/auth/login", "username=" + user + "&password=" + password,
            "application/x-www-form-urlencoded", true).statusCode()).isEqualTo(200);
        return client;
    }

    @Test void anonymousCannotReadScansAndLoginRequiresCsrf() throws Exception {
        var client = client();
        assertThat(send(client, "GET", "/analyses/missing", "", "application/json", false).statusCode()).isEqualTo(401);
        assertThat(send(client, "POST", "/auth/login", "username=admin&password=" + password,
            "application/x-www-form-urlencoded", false).statusCode()).isEqualTo(403);
        assertThat(send(client, "GET", "/health", "", "application/json", false).statusCode()).isEqualTo(200);
        assertThat(send(client, "GET", "/demo/scenarios/safe-change", "", "application/json", false).statusCode()).isEqualTo(200);
    }

    @Test void githubAuthorizationUsesStateAndPkceWithoutLeavingTheBackend() throws Exception {
        var client = client();
        var start = send(client, "GET", "/auth/github/connect", "", "application/json", false);
        assertThat(start.statusCode()).isEqualTo(302);
        assertThat(start.headers().firstValue("location").orElseThrow()).endsWith("/oauth2/authorization/github");
        var authorize = send(client, "GET", "/oauth2/authorization/github", "", "application/json", false);
        assertThat(authorize.statusCode()).isEqualTo(302);
        String location = authorize.headers().firstValue("location").orElseThrow();
        assertThat(location).startsWith("https://github.com/login/oauth/authorize?")
            .contains("client_id=test-client", "state=", "code_challenge=", "code_challenge_method=S256")
            .doesNotContain("test-secret");
    }

    @Test void sessionReportsTheLinkedGithubIdentityWithoutTokens() throws Exception {
        var response = send(login("developer"), "GET", "/auth/session", "", "application/json", false);
        var body = mapper.readTree(response.body());
        assertThat(body.at("/github/status").asString()).isEqualTo("CONNECTED");
        assertThat(body.at("/github/login").asString()).isEqualTo("developer");
        assertThat(body.path("github_connection_required").asBoolean()).isTrue();
        assertThat(response.body()).doesNotContain("access_token", "client_secret", "test-secret");
    }

    @Test void developerCanReadButCannotStartScansOrValidationOrCreateAccounts() throws Exception {
        var client = login("developer");
        assertThat(send(client, "GET", "/analyses/missing", "", "application/json", false).statusCode()).isEqualTo(403);
        for (var route : new String[]{"/analyses", "/scans", "/analyses/missing/validation", "/admin/users"})
            assertThat(send(client, "POST", route, "{}", "application/json", true).statusCode()).isEqualTo(403);
    }

    @Test void analystCanAnalyzeAndValidateButCannotManageAccounts() throws Exception {
        var client = login("analyst");
        assertThat(send(client, "POST", "/analyses", "{}", "application/json", true).statusCode()).isEqualTo(400);
        assertThat(send(client, "POST", "/analyses/missing/validation", "{}", "application/json", true).statusCode()).isEqualTo(403);
        assertThat(send(client, "POST", "/admin/users", "{}", "application/json", true).statusCode()).isEqualTo(403);
    }

    @Test void scanAccessIsExplicitAndDoesNotLeakAcrossAccounts() throws Exception {
        String scanId = "acl-" + UUID.randomUUID();
        scans.save(mapper.createObjectNode().put("scan_id", scanId).put("status", "COMPLETE"));
        String grant = mapper.writeValueAsString(java.util.Map.of("username", "developer", "access", "VIEW"));
        assertThat(send(login("admin"), "POST", "/analyses/" + scanId + "/access",
                grant, "application/json", true).statusCode()).isEqualTo(200);

        assertThat(send(login("developer"), "GET", "/analyses/" + scanId,
                "", "application/json", false).statusCode()).isEqualTo(200);
        var denied = send(login("developer2"), "GET", "/analyses/" + scanId,
                "", "application/json", false);
        assertThat(denied.statusCode()).isEqualTo(403);
        assertThat(mapper.readTree(denied.body()).path("code").asString()).isEqualTo("SCAN_ACCESS_DENIED");

        String escalation = mapper.writeValueAsString(
                java.util.Map.of("username", "developer2", "access", "VALIDATE"));
        var unrelatedAnalyst = send(login("analyst"), "POST", "/analyses/" + scanId + "/access",
                escalation, "application/json", true);
        assertThat(unrelatedAnalyst.statusCode()).isEqualTo(403);
        assertThat(mapper.readTree(unrelatedAnalyst.body()).path("code").asString())
                .isEqualTo("SCAN_SHARE_DENIED");

        String validateGrant = mapper.writeValueAsString(
                java.util.Map.of("username", "analyst", "access", "VALIDATE"));
        assertThat(send(login("admin"), "POST", "/analyses/" + scanId + "/access",
                validateGrant, "application/json", true).statusCode()).isEqualTo(200);
        var validatorShare = send(login("analyst"), "POST", "/analyses/" + scanId + "/access",
                grant, "application/json", true);
        assertThat(validatorShare.statusCode()).isEqualTo(403);
        assertThat(mapper.readTree(validatorShare.body()).path("code").asString())
                .isEqualTo("SCAN_SHARE_DENIED");
    }

    @Test void localSessionWithoutGithubCannotUseProtectedAnalysisServices() throws Exception {
        var client = login("unlinked");
        var response = send(client, "GET", "/analyses/missing", "", "application/json", false);
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(mapper.readTree(response.body()).path("code").asString()).isEqualTo("GITHUB_CONNECTION_REQUIRED");
    }

    @Test void adminCreatesHashedAccountsWithoutReturningPasswords() throws Exception {
        var client = login("admin");
        String user = "user-" + UUID.randomUUID();
        String body = mapper.writeValueAsString(java.util.Map.of("username", user, "name", "New developer",
            "role", "DEVELOPER", "password", password));
        var response = send(client, "POST", "/admin/users", body, "application/json", true);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).doesNotContain(password, "hash");
        assertThat(accounts.loadUserByUsername(user).getPassword()).startsWith("$2a$12$").doesNotContain(password);
        assertThat(send(client, "POST", "/admin/users", body, "application/json", true).statusCode()).isEqualTo(409);
    }

    @Test void authenticatedWritesStillRequireCsrfAndLogoutEndsSession() throws Exception {
        var client = login("admin");
        assertThat(send(client, "POST", "/analyses", "{}", "application/json", false).statusCode()).isEqualTo(403);
        assertThat(send(client, "POST", "/auth/logout", "{}", "application/json", true).statusCode()).isEqualTo(200);
        assertThat(send(client, "GET", "/analyses/missing", "", "application/json", false).statusCode()).isEqualTo(401);
        assertThat(mapper.readTree(send(client, "GET", "/auth/session", "", "application/json", false).body())
            .path("authenticated").asBoolean()).isFalse();
    }

    @Test void credentialsFailuresAreGenericAndSessionCookieIsHttpOnly() throws Exception {
        for (var username : new String[]{"admin", "unknown"}) {
            var response = send(client(), "POST", "/auth/login", "username=" + username + "&password=wrong",
                "application/x-www-form-urlencoded", true);
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(mapper.readTree(response.body()).path("code").asString()).isEqualTo("INVALID_CREDENTIALS");
        }
        var response = send(client(), "GET", "/auth/csrf", "", "application/json", false);
        assertThat(response.headers().firstValue("set-cookie").orElseThrow()).contains("HttpOnly", "SameSite=Lax");
    }

    @Test void repeatedCredentialFailuresAreRateLimited() throws Exception {
        String username = "missing-" + UUID.randomUUID();
        for (int attempt = 0; attempt < 5; attempt++)
            assertThat(send(client(), "POST", "/auth/login", "username=" + username + "&password=wrong",
                "application/x-www-form-urlencoded", true).statusCode()).isEqualTo(401);
        var response = send(client(), "POST", "/auth/login", "username=" + username + "&password=wrong",
            "application/x-www-form-urlencoded", true);
        assertThat(response.statusCode()).isEqualTo(429);
        assertThat(mapper.readTree(response.body()).path("code").asString()).isEqualTo("LOGIN_RATE_LIMITED");
    }

    @Test void unknownGithubUsersCannotSelfProvisionDeveloperAccounts() {
        assertThatThrownBy(() -> accounts.connectGithub("999999", "outsider", "Outsider", null))
            .isInstanceOf(ai.riskgraph.platform.service.PipelineException.class)
            .hasMessageContaining("administrator");
    }
}
