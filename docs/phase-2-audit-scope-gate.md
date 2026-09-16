# Phase-2 audit scope gate

## Decision

Batches 10 and 11 of the consolidated adversarial-audit plan are not activated in
the Week-12 MVP. `AGENTS.md` explicitly limits production analysis to annotation-based
authorization and places principal/privilege graphs and `SecurityFilterChain` parsing
outside the MVP. Implementing either batch would change the product's threat model,
IR semantics, corpus, and release claims; that requires an explicit roadmap and
contract update first.

## Current containment

- Protected-to-protected role/authority changes do not receive `ALLOW`. They emit
  `UNRESOLVED_AUTHORIZATION_DELTA`, lower confidence, mark evidence incomplete, and
  force `REVIEW`.
- Detected `SecurityFilterChain` usage emits
  `UNRESOLVED_SECURITY_FILTER_CHAIN`/`UNRESOLVED_ROUTE_AUTHORIZATION`, lowers
  authorization confidence, and cannot create a high-confidence public/protected fact.
- The partial `AuthorizationResolver` is explicitly marked
  `PHASE 2 — out of current scope` and is disconnected from production extraction.
- Documentation and demo scenarios describe the platform as annotation-only. The
  ADMIN→USER and IDOR fixtures remain design scaffolds, not implemented acceptance
  scenarios.

## Findings covered by the gate

| Finding | Week-12 disposition | Phase-2 closure requirement |
|---|---|---|
| L-03 | Contained: unresolved protected-role delta forces REVIEW | Versioned policy AST and deterministic authorization widening |
| L-04 | Deferred and visibly unsupported | Principal classes plus `HAS_ROLE`/`CAN_ACCESS` reachability |
| L-05 | Partial resolver disconnected; active analyzer fails closed | Integrated ordered filter-chain resolver or removal |
| L-06 | Deferred and visibly unsupported | Method/path matcher tuples with declaration order |
| L-07 | Deferred and visibly unsupported | Deterministic role/authority/access policy parsing |
| C-05 | Contained: experimental code is labeled and disconnected | Integrate after the same scope gate, then remove the scaffold |

No Phase-2 finding may be advertised as implemented until its batch acceptance tests
and corpus evidence pass.
