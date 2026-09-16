# Live React local-mode verification — 2026-09-15

## Scope

This check exercises the audit's TEST-002A acceptance path through the actual React
application at `http://localhost:5173`. It is localhost-only working-tree evidence,
not an independent human review and not evidence for the base commit.

## Preconditions observed

- Platform, dashboard, analyzer, graph-risk, PostgreSQL, AI-validation, and the
  dedicated validation daemon were running through Compose.
- The authenticated account role was Security Analyst.
- GitHub status was `NOT_CONFIGURED`.
- The backend capability was `github_connection_required=false`.

## Browser observations

1. The Account view showed the authenticated verification Analyst.
2. New Analysis displayed enabled repository, old-SHA, and new-SHA fields without a
   GitHub connection gate.
3. The rendered pre-submit review accepted only the allowlisted Compose repository
   `/analysis-repositories/mvp-v2/authorization-removal` and two 40-character SHAs.
4. Submitting commits `2c042ca896ee012303c132131f992e9b67a48ef2` and
   `243d8d27c8dda3100b0f62681f47b241de72bf86` completed successfully.
5. The result rendered risk `22 -> 91`, delta `+69`, one new anonymous-sensitive
   path, HIGH extraction confidence, verdict `BLOCK`, and validation `CONFIRMED`.
6. Saved Scans exposed the same repository result to the Analyst.
7. The browser session was signed out after verification.

The temporary known browser-test password was not recorded in this evidence file.
The existing ignored verification credential was restored after sign-out.
