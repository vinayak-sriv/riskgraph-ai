# Mentor Demo — Current Fixture Vertical Slice

## Start

```powershell
docker compose -f infrastructure/docker-compose.yml --profile future-services up -d --build
docker compose -f infrastructure/docker-compose.yml --profile future-services ps
```

Open `http://localhost:5173`.

## What the Demo Proves

1. The same endpoint IR contract represents code before and after authorization
   removal.
2. NetworkX builds both graphs and BFS discovers one new path from Anonymous to
   the Customer data resource.
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

The demo does not yet claim real Java AST extraction, Ollama explanation, or
Docker HTTP exploit validation. Those remain later pipeline stages.

## Next Presentation Milestone

Implement Weeks 4–6 before presenting the system as live code analysis:

1. Select a local sample repository and immutable before/after commit SHAs.
2. Show the Git diff where `@PreAuthorize` was removed.
3. Generate both IR documents automatically with Spoon and retain file/line evidence.
4. Feed generated IR into the existing graph/risk/platform/dashboard pipeline.
5. Display extraction coverage and unsupported constructs beside the verdict.

Ollama and Docker validation strengthen the explanation and confirmation story, but
they are not prerequisites for the first honest real-code mentor demonstration.
