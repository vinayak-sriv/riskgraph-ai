# Pending post-MVP updates

This document records approved roadmap additions. It does not describe features that
are currently implemented or supported. The existing Java/Spring Boot MVP remains the
release priority, and these tracks begin only after independent external review and
the final Java release.

## Delivery order

1. Close the Week 12 human-review gate and release the Java/Spring Boot MVP.
2. Add language/framework preflight and explicit unsupported-framework results.
3. Add the notification event, preference, and delivery foundation for existing
   Spring Boot findings.
4. Build and evaluate the FastAPI analyzer without changing the downstream IR.
5. Promote FastAPI from experimental to supported only after its acceptance gate.
6. Evaluate additional notification channels and Python frameworks separately.

## Track A — Python web analysis

### Supported claim

The first claim is **Python 3 FastAPI web applications**, not every Python repository.
Generic scripts, data-science projects, desktop applications, Django, and Flask are
unsupported until a deterministic adapter is implemented and evaluated.

### Batches

1. **Preflight:** detect repository languages, framework markers, modules, and mixed
   repositories before analysis.
2. **Analyzer boundary:** select the Spoon Spring analyzer or a separate Python
   analyzer through a versioned interface; keep the Stage 3 endpoint IR unchanged.
3. **FastAPI extraction:** use Python AST evidence to extract router prefixes,
   route decorators, HTTP methods, handlers, authentication dependencies, security
   scopes, and source locations.
4. **Call/resource resolution:** deterministically resolve supported handler → service
   → repository/resource paths, beginning with explicit calls and SQLAlchemy-style
   access. Dynamic or ambiguous behavior produces diagnostics rather than invented
   edges.
5. **Parity:** implement the four MVP scenarios—authorization removal, safe change,
   new public sensitive endpoint, and sensitive resource exposure—and reuse the
   existing graph, risk, AI, validation, decision, dashboard, GitHub, and reporting
   stages.
6. **Evaluation:** pin licensed public FastAPI repositories and immutable commits;
   record framework/version, source hashes, reviewer attribution, coverage,
   diagnostics, runtime, and deterministic results.

### Unsupported and partial behavior

- Python without a supported framework returns `UNSUPPORTED_FRAMEWORK` and no risk
  score or ALLOW/BLOCK verdict.
- A mixed repository may analyze supported modules, but the result must list excluded
  modules and report partial coverage.
- Unsupported FastAPI patterns lower confidence and force REVIEW when they may affect
  a security path.
- The dashboard and GitHub Check must explain the supported framework boundary before
  repository owners rely on the result.

### Promotion gate

FastAPI becomes officially supported only when extraction, provenance,
coverage/diagnostics, all four scenarios, clean-checkout automation, one local-Docker
validation fixture, and independently reviewed public-repository evaluation pass.
Django and Flask remain separate adapter projects.

## Track B — notification manager

### Product behavior

The notification manager consumes final decision events and is independent of the
source language. The initial channels are the authenticated in-app notification
center and email. It sends actionable notifications for REVIEW and BLOCK and may send
an optional resolution notice after a later clean scan.

Only verified RiskGraph users may subscribe. Repository access is rechecked before
delivery, project administrators choose recipients and severity thresholds, and every
recipient can control channels or unsubscribe.

### Message contract

Messages include the repository and pull request, finding category, risk before/after
and delta, confidence, validation status, and a protected link to the PR or dashboard.
Templates say **possible** or **inferred** unless Docker validation confirmed the
specific behavior. Email or push content never contains raw source, credentials,
tokens, sensitive response bodies, or unrestricted evidence.

### Reliability and security batches

1. Define versioned final-decision and notification-event contracts.
2. Add additive PostgreSQL migrations for preferences, repository subscriptions,
   notification events, and delivery attempts.
3. Implement a transactional outbox so scan completion and event creation cannot
   diverge.
4. Add in-app delivery and read/unread state.
5. Add email behind a swappable provider interface and environment-based
   configuration; never hardcode provider credentials.
6. Deduplicate by recipient, channel, finding fingerprint, and pull-request head SHA;
   bound retries with backoff, rate limits, dead-letter state, and audit records.
7. Test permission revocation, duplicate events, provider outages, template redaction,
   stale findings, resolution notices, unsubscribe behavior, and delivery recovery.
8. Consider browser/mobile push only after consent and privacy review. Treat SMS and
   third-party messaging as separate future integrations.

### Notification acceptance gate

- No notification is delivered without current repository access and opt-in policy.
- ALLOW scans do not create warning notifications.
- REVIEW/BLOCK delivery is idempotent for the same finding and PR head.
- Inferred and confirmed wording cannot be confused.
- Provider failure cannot change the security verdict or lose the audit record.
- No secret or raw sensitive evidence appears in a template, log, or delivery record.
