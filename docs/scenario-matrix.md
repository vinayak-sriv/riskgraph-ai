# Scenario Matrix

The final MVP demo must reliably cover four scenarios without crossing into Phase 2.

| Scenario | Contract Fixture | Expected Direction |
|---|---|---|
| Authorization removal | `contracts/ir/examples/auth-removal-before.json` and `auth-removal-after.json` | Risk increases sharply; final decision becomes `BLOCK` after validation confirms exposure |
| Safe cosmetic change | `contracts/ir/examples/safe-change.json` | No new paths; risk unchanged; final decision remains `ALLOW` |
| New public sensitive endpoint | `contracts/ir/examples/public-sensitive-endpoint.json` | Detect the new unauthenticated route to a HIGH-sensitivity resource and run an authorization-focused HTTP validation |
| Sensitive resource exposure | Fixture to be added before Week 7 | Detect an existing public route newly reaching a HIGH-sensitivity repository/resource |

## Scope Note

Full IDOR, object-ownership, privilege-expansion, and taint/data-flow analysis remain
Phase 2. Existing `privilege-expansion-*.json` files are design scaffolds, not MVP
acceptance fixtures.
