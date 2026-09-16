# Tests

The Python test tree is organized by responsibility:

- `ai_validation/`: structured AI behavior, degraded mode, and Docker validation.
- `contract/`: JSON Schema and OpenAPI compatibility.
- `dataset/`: corpus integrity and provenance.
- `graph_risk/`: graph construction, reachability, scoring, and containment.
- `integration/`: cross-module workflows.
- `release/`: packaging, reporting, container, and public-release invariants.
- `reporting/`: Check, SARIF, and summary output.
- `tools/`: development and verification utilities.
- `fixtures/`: shared deterministic test data.

Java service tests live beside their Maven modules, and dashboard tests live under
`apps/dashboard`.

Run the complete local gate from the repository root:

```powershell
python tools/dev/verify_release.py
```

Tests that require Docker use only generated local repositories and registered
sandbox images. They never target external applications.
