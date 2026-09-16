# Reproducible demo recording

The Week 14 recording is generated from the dashboard's deterministic fixtures. It
does not contact a live application, public repository, or third-party target.

## Generate the recording

```powershell
cd apps/dashboard
npm ci
npx playwright install chromium
npm run demo:record
```

The command writes:

- `dist/riskgraph-demo.webm`
- `dist/riskgraph-demo.manifest.json`

The manifest records the source commit, byte length, SHA-256 digest, generation time,
and the recording's local-fixture boundary. GitHub CI runs the same command and keeps
the resulting artifact for 14 days.

## Walkthrough content

The recording shows the authorization-removal BLOCK result, switches between graph
comparison modes, demonstrates that a safe cosmetic change remains ALLOW, shows a
new public sensitive endpoint returning BLOCK, and restores the main authorization
removal scenario. It is a product walkthrough, not independent evaluation evidence.

For a live Docker demonstration, follow [Mentor demo](mentor-demo.md). Live validation
must use only the registered local sandbox application.
