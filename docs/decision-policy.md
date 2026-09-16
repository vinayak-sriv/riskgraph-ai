# Deterministic decision policy v1

Preliminary BLOCK: new anonymous sensitive path AND risk_after >=61.
REVIEW: risk_after >=41, risk_delta >=21, or incomplete/LOW/MEDIUM extraction evidence.
ALLOW: no applicable review/block condition. Failed requests return a structured
FAILED response with REVIEW, never ALLOW.

| Preliminary | CONFIRMED | REJECTED | INCONCLUSIVE / ERROR | NOT_RUN |
|---|---|---|---|---|
| BLOCK | BLOCK | BLOCK | BLOCK | BLOCK |
| REVIEW | BLOCK | REVIEW | REVIEW | REVIEW |
| ALLOW | BLOCK | ALLOW | REVIEW | ALLOW |

A 401/403 rejects the tested anonymous-access hypothesis. It does not disprove all
static evidence. A 200 confirms only when the shipped marker body matches and the
registered commit/image identity matches. Other responses and timeouts are
inconclusive/error. Cleanup failure invalidates confirmation.

The generic `/sandbox-demonstration/` operation leaves the source scan's final
verdict unchanged. `/validation` applies the policy only to a registered source-bound
fixture. AI status never changes risk, extraction confidence, or verdict.

Both before and after component scores are exposed. Categories use 0–20 LOW,
21–40 MODERATE, 41–60 MEDIUM, 61–80 HIGH, 81–100 CRITICAL. The existing Customer
golden fixture deliberately labels CRITICAL (22→91); default analyzer policy labels
Customer HIGH (18→87). The runnable Payment fixture is CRITICAL and yields 22→91
without changing policy weights or falsifying Customer classification.

When one change creates multiple newly reachable route/resource states, the reported
component vector is selected by an explicit deterministic rank: highest post-change
risk, then largest risk delta, then stable route ID, then stable resource ID. Only
primitive ranking fields participate; `RawScores` values are never implicitly
ordered. All new paths remain in graph evidence regardless of which state supplies
the single summary component vector.
