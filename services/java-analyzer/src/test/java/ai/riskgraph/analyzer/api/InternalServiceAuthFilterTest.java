package ai.riskgraph.analyzer.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalServiceAuthFilterTest {
    @Test
    void rejectsMissingCredentialAndAcceptsMatchingCredential() throws Exception {
        var filter = new InternalServiceAuthFilter("internal-secret");
        var missingRequest = new MockHttpServletRequest("POST", "/analyze");
        var missingResponse = new MockHttpServletResponse();
        filter.doFilter(missingRequest, missingResponse, new MockFilterChain());
        assertThat(missingResponse.getStatus()).isEqualTo(401);

        var request = new MockHttpServletRequest("POST", "/analyze");
        request.addHeader("X-RiskGraph-Service-Token", "internal-secret");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void failsClosedWhenServiceCredentialIsNotConfigured() throws Exception {
        var response = new MockHttpServletResponse();
        new InternalServiceAuthFilter("").doFilter(
                new MockHttpServletRequest("POST", "/analyze"), response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(503);
    }

    @Test
    void rejectsCredentialForAnotherServiceAudience() throws Exception {
        var request = new MockHttpServletRequest("POST", "/analyze");
        request.addHeader("X-RiskGraph-Service-Token", "graph-only-secret");
        var response = new MockHttpServletResponse();
        new InternalServiceAuthFilter("analyzer-only-secret")
                .doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void leavesHealthEndpointPublic() throws Exception {
        var response = new MockHttpServletResponse();
        new InternalServiceAuthFilter("").doFilter(
                new MockHttpServletRequest("GET", "/health"), response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
