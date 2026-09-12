# Mentor Demo — Source Analysis and Docker Validation

## Start

```powershell
python tools/dev/create_mvp_samples.py
python tools/dev/init_auth.py
python tools/dev/build_sandboxes.py --prepare-only
docker compose -p riskgraph-mvp -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.validation.yml --profile app up -d --build
python tools/dev/verify_mvp.py --compose --validation
```

Open `http://localhost:5173`.
Sign in as `admin` using the ignored `tmp/local-auth/admin.password` file.
For the live AI demo include the Ollama overlay and model from the runtime guide.
Use **Manage accounts** to create a Developer or Security Analyst. Developers can
open only scan IDs explicitly shared with them and cannot start analysis or validation.

## What the Demo Proves

1. The same endpoint IR contract represents code before and after authorization
   removal.
2. NetworkX builds both graphs and BFS discovers one new path from Anonymous to
   the sensitive data resource (Payment in the source fixture).
3. The transparent weighted formula changes risk from `22` to `91`.
4. The deterministic decision engine returns `BLOCK`.
5. The React dashboard obtains this result through the Spring platform API; it
   does not call the graph service directly.

## Verification

```powershell
python -m pytest tests/contract tests/graph_risk -q
mvn -f services/platform-api/pom.xml test
npm --prefix apps/dashboard run build
```

The dashboard distinguishes saved fixtures from source results. Live source analysis
uses Spoon. Ollama integration is implemented and degrades explicitly if the local
instance is unavailable. The registered authored application supports live Docker
validation bound to the scanned commit. AI text alone never confirms a finding.

## Source Walkthrough

1. Select all four fixture scenarios first. Explain their saved-demonstration label.
2. Open `samples/generated/mvp-v1/manifest.compose.json`. Copy the authorization-removal
   repository path and immutable SHAs into the dashboard and click **Run Analysis**.
   Native mode uses paths in `manifest.json` instead.
3. Show **22 → 91, delta +69, BLOCK**; toggle Before, After and Compare. Select graph
   nodes and follow Anonymous → endpoint → service → repository → Payment.
4. Show source file/lines, SHAs, analyzer version, HIGH confidence and 100% coverage.
   Payment is CRITICAL under the sensitivity policy. Customer HIGH examples score
   differently; the scoring formula is never overridden to fit a demonstration.
5. Show the six components and the unconfirmed AI hypothesis. DEGRADED means a stage
   is unavailable or evidence is incomplete; inspect its displayed reason.
6. Click **Validate registered Docker sandbox**. The vulnerable commit returns the
   expected HTTP 200 marker, becomes CONFIRMED, and retains BLOCK. The protected
   revision returns 403. Only the authored registered app has a sandbox adapter.
7. Run the other manifest pairs: safe **22 → 22 ALLOW**, new public endpoint
   **0 → 65 BLOCK**, sensitive resource exposure **8 → 65 BLOCK**. The legacy exposure
   fixture starts at 4 (LOW input); the real Catalog source is MODERATE and starts at 8.
8. Export JSON. Inspect `tmp/evidence-bundle` and the offline Check/SARIF/summary from
   the local GitHub walkthrough in `docs/runtime-guide.md`. No GitHub writes occur.
9. Explain that the 100-record corpus is provisional and parameterized. Passing it
   establishes regression behavior, not externally reviewed real-world accuracy.

Keep saved fixtures and the exported evidence bundle available as a presentation
fallback. Details and limits are in `docs/verification-report.md`.
