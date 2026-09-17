# GitHub sign-in for a local, open-source RiskGraph installation

GitHub App user authorization is implemented by the Spring Boot platform API. The existing username/password login remains available so an administrator can bootstrap roles and link an existing Analyst or Admin account.

## Local hosting works

For a browser and backend running on the same computer, GitHub can redirect the browser back to a local callback. A public domain or tunnel is unnecessary for sign-in. The browser and backend still need internet access to complete authentication. GitHub documents this redirect flow and loopback support in [Authorizing OAuth apps](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps#redirect-urls).

Keep `localhost` consistent across this project's dashboard, API URL, allowed origin, and callback. If using `127.0.0.1` instead, change all of them together. If other computers access one shared LAN server, configure the server hostname instead: `localhost` always refers to the computer running that browser.

## Register the sign-in application

In GitHub, open Settings → Developer settings → GitHub Apps and configure the existing RiskGraph app with these values for the current local ports:

| GitHub field    | Value                                            |
| --------------- | ------------------------------------------------ |
| GitHub App name | RiskGraph AI Local                               |
| Homepage URL    | `http://localhost:5173`                          |
| Callback URL    | `http://localhost:8080/login/oauth2/code/github` |

Generate a client secret after registration and keep it on the backend. This flow does not need Device Flow, wildcard callback matching, or OAuth scopes; GitHub App user tokens use the app's fine-grained permissions. See [GitHub's user access-token flow](https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-user-access-token-for-a-github-app).

The callback belongs to the backend on port 8080. After handling it, the backend should redirect to the configured dashboard origin on port 5173. Spring's default callback template is `{baseUrl}/login/oauth2/code/{registrationId}`. See [Spring Security OAuth2 configuration](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/core.html).

## Implemented authentication flow

The Spring Boot platform already implements the authorization-code flow with PKCE, session cookies, CSRF protection, and deny-by-default access rules. A self-hosted installation enables the flow by supplying its own GitHub App client credentials and restarting the platform API.

| Implemented area                       | Responsibility                                                                                                          |
| -------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| `services/platform-api`                | Owns the authorization-code exchange, PKCE, stable GitHub numeric-ID mapping, local role mapping, and HttpOnly session. |
| `V004__github_external_identities.sql` | Stores the stable provider subject separately from the mutable GitHub login and supports OAuth-only accounts.           |
| `/auth/session`                        | Returns the local account plus `github.status`; it never returns the client secret or GitHub token.                     |
| Dashboard account view                 | Starts `/auth/github/connect` with full browser navigation and refreshes gated views from the returned session.         |

For admission, I recommend an allowlist or invitation. If open registration is intentionally enabled, assign Developer by default; only a local administrator should grant Analyst/Admin privileges. GitHub authentication establishes identity, while RiskGraph roles govern access.

## Backend configuration

### Browser sign-in credentials

The browser sign-in flow requires the GitHub App client ID and client secret. Put them in the ignored root `.env`; never commit real values:

```dotenv
GITHUB_CLIENT_ID=replace_with_github_client_id
GITHUB_CLIENT_SECRET=replace_with_github_client_secret
DASHBOARD_ORIGIN=http://localhost:5173
```

The repository's local launcher loads this ignored file before starting the platform. Docker deployments should inject the same values into `platform-api` through their secret-management mechanism. OAuth remains optional, so self-hosters can retain local password login without GitHub credentials.

Start the local stack and open the account view:

```powershell
python tools/dev/start_local.py
```

The account card should show **Not connected** and an enabled **Continue with GitHub** action. **Backend setup required** means one or both credentials were absent when the platform started; update the environment and restart it. The callback must remain exactly `http://localhost:8080/login/oauth2/code/github` for the local ports above.

RiskGraph uses a `SameSite=Lax` HttpOnly session cookie so the top-level callback can retain the saved authorization request. CSRF protection, OAuth state verification, and PKCE remain enabled. Use Secure cookies with HTTPS outside local development. See [MDN's cookie rules](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Set-Cookie#samesitesamesite-value).

Browser authorization proves identity and links the GitHub account to a local RiskGraph role. Repository installation discovery, PR events, cloning, and Checks remain separate capabilities; the dashboard must not represent them as synchronized unless their backend checks succeed.

## Open-source credential model

Publish source and configuration placeholders. Each independently hosted installation should register its own application and supply its own client ID/secret through backend environment variables or an ignored local environment file loaded by its launcher. Individual users signing into that installation do not each need to register an app.

Never ship one shared secret in the repository or Docker image. Do not put the secret in a `VITE_*` variable, browser code, or browser storage. Keep GitHub tokens server-side and return a normal HttpOnly session cookie to React. If login is the only use, avoid retaining GitHub tokens beyond what is needed; if retained for API access, protect them and handle expiration/revocation. These choices follow [GitHub's credential guidance](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/best-practices-for-creating-an-oauth-app).

## Repository and PR integration

RiskGraph uses the GitHub App model for selected-repository access and granular permissions. Sign-in is only the identity-linking step; installation access and repository permissions are checked independently. See [GitHub Apps versus OAuth Apps](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/differences-between-github-apps-and-oauth-apps).

Webhooks differ from login callbacks: GitHub's servers must reach the webhook receiver. Local development therefore needs a reachable endpoint or a forwarding service such as Smee for webhooks. See [GitHub's local webhook guidance](https://docs.github.com/en/webhooks/testing-and-troubleshooting-webhooks/testing-webhooks).

## Acceptance checks before enabling it

Test successful login, canceled consent, expired/mismatched state, session expiration, account linking, denied admission, logout, and unchanged Developer/Analyst/Admin restrictions. Confirm that tokens and secrets never enter frontend responses or logs. Keep username/password login available for offline operation.
