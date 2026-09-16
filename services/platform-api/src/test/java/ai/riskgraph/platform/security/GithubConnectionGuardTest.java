package ai.riskgraph.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

class GithubConnectionGuardTest {
    @Test
    void explicitLocalModeDoesNotRequireAnUnavailableGithubConnection() {
        var guard = new GithubConnectionGuard(mock(AccountService.class), false);

        assertThatCode(guard::requireLinked).doesNotThrowAnyException();
    }

    @Test
    void sessionPublishesTheSameGithubRequirementUsedByTheGuard() {
        var accounts = mock(AccountService.class);
        var settings = mock(GithubOAuthSettings.class);
        var guard = new GithubConnectionGuard(accounts, false);
        when(accounts.githubConnection(null, false)).thenReturn(new AccountService.GithubConnection(
            AccountService.GithubStatus.NOT_CONFIGURED, null, java.util.List.of(), null));

        var result = new AccountController(accounts, settings, guard)
            .session(null, mock(HttpServletRequest.class));

        assertThat(result).containsEntry("authenticated", false)
            .containsEntry("github_connection_required", false);
        assertThat(result.get("github")).isEqualTo(new AccountService.GithubConnection(
            AccountService.GithubStatus.NOT_CONFIGURED, null, java.util.List.of(), null));
    }
}
