package ai.riskgraph.platform.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

class GithubConnectionGuardTest {
    @Test
    void explicitLocalModeDoesNotRequireAnUnavailableGithubConnection() {
        var guard = new GithubConnectionGuard(mock(AccountService.class), false);

        assertThatCode(guard::requireLinked).doesNotThrowAnyException();
    }
}
