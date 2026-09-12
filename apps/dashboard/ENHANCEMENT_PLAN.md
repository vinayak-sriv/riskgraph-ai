# RiskGraph AI dashboard enhancement plan

Prepared with Lovable on September 11, 2026 and reconciled against the current React dashboard.

## Implementation status

Implemented in the current dashboard: the four workspace destinations; Verdict + After Risk decision hierarchy; shared Before/After risk track; consolidated graph toolbar; explicit legend; changed/path filtering; synchronized comparison; full-screen inspection; deterministic evidence-to-node and node-to-source links; inline source validation and pre-submit review; standardized preserved-result request states; permanent AI separation; mobile findings/path/node-directory priority; and explicit Confirmed, Inferred, Ambiguous, and Unresolved certainty.

Backend-dependent work remains represented honestly in the UI. Searchable and sortable scan history will activate when a scan-listing API exists, and GitHub connection actions will activate after the platform implements OAuth. No client-side mock data or unsupported API contract was introduced for either capability.

## Product principle

The interface should help an analyst answer three questions in order:

1. Did this code change alter security risk?
2. What deterministic evidence caused the verdict?
3. Where in the before/after graph and source provenance can that evidence be verified?

The dashboard must preserve the current API contracts, risk formula, role restrictions, graph behavior, and distinction between deterministic evidence and optional AI interpretation. It must not introduce simulated SOC telemetry, incidents, or live activity that the platform does not produce.

## What already works

The current redesign already provides the violet token system, responsive sidebar and mobile navigation, decision-first overview, Before/After/Delta metrics, graph comparison modes, keyboard-operable nodes, node inspection, a text graph directory, evidence and provenance sections, explicit AI labeling, loading and degraded feedback, reduced-motion support, and responsive layouts.

The next iteration should refine organization and investigation speed rather than replace these foundations.

## Recommended information architecture

Move from one long page with anchor navigation to four clear workspace destinations:

| Destination  | Purpose                         | Primary content                                            |
| ------------ | ------------------------------- | ---------------------------------------------------------- |
| Analysis     | Review the active result        | Verdict, risk delta, graph, evidence, technical details    |
| New analysis | Configure and run analysis      | Source, revisions, validation options, submission review   |
| Saved scans  | Find previous results           | Searchable and sortable scan table with role-aware actions |
| Account      | Manage identity and connections | User role, session state, GitHub connection                |

Keep the Analysis page in this fixed order: decision summary, risk comparison, new paths, graph comparison, deterministic evidence, scoring, provenance and diagnostics, then AI interpretation.

## Component improvements

| Area               | User problem                                                 | Proposed improvement                                                                                                                                     | Affected components                                 | Complexity                                                                                  |
| ------------------ | ------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------- | ------------------------------------------------------------------------------------------- |
| Decision summary   | Important values still compete for attention                 | Make Risk Delta the dominant value; keep Before and After as a paired supporting comparison; link the new-path count directly to the selected graph path | `Overview`, shared metric primitives                | Medium                                                                                      |
| Risk comparison    | Separate meters require extra visual comparison              | Add one shared 0–100 delta track with Before and After markers and a labeled span between them; retain the accessible numeric table                      | `Overview`, new `RiskDeltaTrack`                    | Medium                                                                                      |
| Graph controls     | Controls are distributed across the graph section            | Use one toolbar for view mode, path selection, diff filtering, fit, zoom, full-screen, and optional synchronized view position                           | `GraphComparison`, `SecurityGraph`                  | Large                                                                                       |
| Graph legend       | New users must infer several visual encodings                | Add a compact, collapsible legend using color, shape, icon, and text for node type and change state                                                      | `GraphComparison`, new `GraphLegend`                | Small                                                                                       |
| Evidence tracing   | Graph and evidence are readable but separate                 | Allow an evidence item to focus its related graph nodes, and let the node inspector link back to matching evidence and source locations                  | `GraphComparison`, `NodeInspector`, `EvidencePanel` | Large                                                                                       |
| Evidence density   | Long findings and audit data create scrolling                | Present findings as filterable expandable cards; move provenance and diagnostics into a persistent Technical details disclosure                          | `EvidencePanel`                                     | Medium                                                                                      |
| Risk components    | Six components are harder to compare as repeated cards       | Use a compact table with weight, Before, After, contribution, and change; optionally add a stacked contribution bar above it                             | `EvidencePanel`                                     | Medium                                                                                      |
| AI interpretation  | It can be mistaken for confirmed evidence                    | Retain physical separation and add a dashed violet boundary plus the permanent label “AI interpretation — unconfirmed”                                   | `EvidencePanel`, new `AiPanel`                      | Small                                                                                       |
| New analysis       | The source form asks for several technical inputs at once    | Use three steps: Source, Options, Review. Preserve entered values between steps and show inline commit/scan validation                                   | `AnalysisSetup`                                     | Large                                                                                       |
| Saved scans        | Opening a scan by ID does not support discovery              | Add a searchable table with repository, revision pair, verdict, delta, confidence, validation, date, and permitted actions                               | `AnalysisSetup`, new `SavedScansTable`              | Large; requires an existing list API or a backend addition in the planned integration phase |
| Account and GitHub | Authentication and repository connection states are implicit | Add a GitHub connection card with disconnected, connecting, connected, expired, and error states; show account and granted permissions                   | `AccountPanel`, future OAuth integration            | Medium after backend OAuth support exists                                                   |
| Request states     | A spinner alone gives limited context                        | Use geometry-matched skeletons for first load, preserve existing results during refresh, and standardize empty, error, degraded, and success states      | new `StateBoundary`, existing panels                | Medium                                                                                      |
| Mobile graph       | A dense canvas remains difficult below tablet size           | Show the selected path and node directory first, with a clear action to open the interactive graph full-screen                                           | `GraphComparison`, `SecurityGraph`                  | Medium                                                                                      |

## Visual refinement

- Keep `#0B0D1B` as the page base, `#5F01FB` as the primary action color, `#514A85` for depth, and `#FFFFFF` for high-priority text.
- Reserve amber for warnings and elevated review states, and red for critical or blocked states.
- Use gradients on primary actions, selected graph paths, and the decision surface. Avoid placing gradients behind dense tables or long text.
- Standardize panel padding, border radius, divider opacity, shadows, and heading spacing through tokens.
- Use monospace with tabular numerals only for scores, hashes, IDs, endpoints, and timestamps.
- Reduce simultaneous glowing borders. Give the strongest glow to the current decision or selected graph path.
- Keep tables visually quiet: sticky headers, aligned numeric columns, subtle row hover, and a clear selected row.

## Responsive behavior

| Viewport                  | Layout                                                                                                                  |
| ------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| Desktop, 1280px and wider | Persistent rail, 12-column content, side-by-side graphs, two-column supporting evidence                                 |
| Tablet, 768–1279px        | Collapsed icon rail, stacked graphs or segmented Before/After view, single-column evidence                              |
| Mobile, below 768px       | Bottom navigation, decision and delta in the first viewport, text path before graph canvas, inspector as a bottom sheet |

## Interaction and accessibility rules

- Keep focus visible on every control and restore focus when drawers, dialogs, and full-screen graph views close.
- Ensure all graph functions have keyboard-accessible controls and a readable text equivalent.
- Never rely on color alone; pair severity and change colors with labels and icons.
- Announce analysis completion, validation results, errors, and degraded stages through polite live regions.
- Limit motion to short opacity and transform transitions around 150–180 ms and disable them under reduced motion.
- Keep touch targets at least 44px where space permits.
- Preserve the current result during refresh or validation and label which operation is running.

## Performance plan

- Memoize Dagre layout by analysis revision and layout direction.
- Recalculate graph layout after resize settles rather than on every resize event.
- Lazy-load the graph bundle after the decision summary is usable.
- Virtualize evidence or scan rows only after real datasets show a need; use a threshold so small lists remain simple.
- Avoid continuous graph edge animation and large-area backdrop blur.

## Delivery order

### Phase 1 — must have

1. Introduce the four-destination workspace structure while preserving current workflows.
2. Add the shared Risk Delta track and tighten the first-viewport decision hierarchy.
3. Consolidate graph controls and add the explicit graph legend.
4. Strengthen visual separation of deterministic evidence and AI interpretation.
5. Standardize empty, loading, error, degraded, and success presentation.

### Phase 2 — high value

1. Add graph-to-evidence and evidence-to-graph deep links.
2. Add diff filters, full-screen graph mode, and optional synchronized comparison views.
3. Convert New analysis to a Source → Options → Review workflow.
4. Introduce the Saved scans table when a list endpoint is available.
5. Add the GitHub connection card after OAuth support is implemented in the platform API.

### Phase 3 — polish after usage testing

1. Add the risk-component contribution visualization.
2. Add saved view and graph-layout preferences.
3. Introduce virtualization when measured data volume justifies it.
4. Add compact export/copy actions for provenance and selected evidence bundles.

## Acceptance criteria

1. Every currently displayed data field and authorized action remains available without changing an API contract.
2. Verdict, Risk Before, Risk After, Risk Delta, confidence, and new-path count are visible in the first desktop and mobile viewport.
3. An analyst can move from a finding to its graph path and source provenance in two actions or fewer.
4. Every graph exposes its nodes, edges, and new paths through a keyboard-accessible text view.
5. AI content remains visually separated and labeled as unconfirmed on every breakpoint.
6. Empty, loading, error, degraded, and success states are defined for every asynchronous data surface.
7. All interactive controls have visible keyboard focus, and no status depends on color alone.
8. Motion is disabled when reduced motion is requested.
9. Primary text and status combinations meet WCAG AA contrast against their actual surfaces.
10. Existing regression tests, TypeScript compilation, and the production build continue to pass after each phase.

## Measurement

Validate the iteration with task-based usability checks: identify the verdict in five seconds, explain the largest risk change in 30 seconds, locate the affected path and source evidence in 60 seconds, and start a new analysis without documentation. Track completion rate, time, wrong-path clicks, and keyboard-only completion.
