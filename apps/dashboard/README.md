# RiskGraph dashboard

React + TypeScript frontend for the existing RiskGraph platform. It reads only from the Spring Boot platform API, never directly from the analyzer, graph/risk service, AI/validation service, or database.

## Run and verify

```powershell
npm.cmd ci
npm.cmd run dev
npm.cmd test
npm.cmd run build
```

Open `http://localhost:5173`. `VITE_PLATFORM_API_BASE_URL` defaults to `http://localhost:8080`; set it at build/dev-server startup to use another configured platform endpoint.

## Workflows

- Four demo scenarios: `GET /demo/scenarios/{scenario}`. An unavailable API falls back to a clearly labeled saved fixture.
- Sign-in/session/logout and CSRF use the existing `/auth/*` endpoints with session cookies.
- Security Analysts and Admins can submit immutable source revisions to `POST /analyses` and request registered Docker validation with `POST /analyses/{scan_id}/validation`.
- Signed-in users can retrieve a saved result through `GET /analyses/{scan_id}`. Developers retain view-only controls. Admin account management uses `/admin/users`.
- The Analysis workspace contains the effective verdict and After Risk as its primary signal, a shared Before/After track, risk delta, paths, before/after graphs, deterministic evidence, scoring, provenance, diagnostics, and separately labeled AI interpretation.
- Graphs support Compare/Before/After modes, changed/path filtering, synchronized viewports, full-screen inspection, node-type highlighting, zoom, pan, node dragging, and a text directory. Evidence and exact source rows link to deterministically matching nodes. Copy uses original path IDs; Export JSON preserves the complete response.
- Hash routes separate Analysis, New analysis, Saved scans, and Account. Theme preference persists locally; dark mode is the default. Layouts adapt to desktop, tablet, and mobile, with reduced-motion and reduced-transparency alternatives.
- Saved scans accept known IDs and show the active source result. Searchable history remains explicitly pending until the platform exposes a listing API. The GitHub connection card likewise reports that backend OAuth support is required before connection can be enabled.

See [REDESIGN.md](./REDESIGN.md) for the research references, change-by-change rationale, architecture, verification, and tradeoffs.

For proposed local GitHub sign-in setup, see [GITHUB_AUTH_SETUP.md](./GITHUB_AUTH_SETUP.md). This guide does not enable OAuth by itself.
