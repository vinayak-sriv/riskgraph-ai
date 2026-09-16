# RiskGraph dashboard

The RiskGraph dashboard is a React and TypeScript interface for reviewing security
analysis results. It communicates only with the Spring Boot platform API; browser
code never connects directly to the analyzer, graph/risk service, AI/validation
service, Docker daemon, or database.

## Features

- Authenticated analysis, scan history, cancellation, and account views.
- Before/after security graphs with compare modes, filters, synchronized viewports,
  full-screen inspection, zoom, pan, and accessible text alternatives.
- Deterministic evidence, risk components, source provenance, extraction confidence,
  and diagnostics displayed separately from optional AI interpretation.
- Source-bound validation results with explicit image, commit, probe, and cleanup
  provenance.
- Responsive light/dark layouts with reduced-motion and reduced-transparency support.

## Development

From `apps/dashboard`:

```powershell
npm ci
npm run dev
npm run lint
npm run format:check
npm test
npm run test:coverage
npm run test:e2e
npm run build
```

Open [http://localhost:5173](http://localhost:5173). The platform API defaults to
`http://localhost:8080`; set `VITE_PLATFORM_API_BASE_URL` before starting or building
the app to use another configured endpoint.

Design rationale and accessibility decisions are documented in
[`REDESIGN.md`](REDESIGN.md). Optional GitHub sign-in configuration is described in
[`GITHUB_AUTH_SETUP.md`](GITHUB_AUTH_SETUP.md).
