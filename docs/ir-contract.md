# IR Contract

The intermediate representation is the stable contract between source analysis
and downstream graph/risk logic. Downstream services consume IR JSON only, never
raw Java source code.

The canonical schema is:

```text
contracts/ir/endpoint-ir.schema.json
```

Analyzer provenance and evidence wrap the canonical IR using:

```text
contracts/ir/analysis-envelope.schema.json
```

The envelope adds immutable commit provenance, deterministic analysis identity,
changed ranges, qualified method identity, every resolved dependency path,
sensitivity-policy evidence, diagnostics, and extraction confidence. These fields
do not alter the downstream endpoint IR object.

Envelope schema `1.1.0` adds a sensitivity classification to every dependency
path. The platform expands those paths into separate canonical route/path records
with the same stable route ID before graph construction. Unresolved mappings and
ambiguous call targets are diagnostic evidence only and never become graph routes.

Each newly exposed route/resource/path receives a SHA-256 `finding_id` derived
from its scan, route, resource, and dependency path. AI explanations and sandbox
validation results are stored beneath that finding and cannot update sibling
findings.

Required fields:
- `endpoint`
- `method`
- `controller`
- `authentication`
- `required_role`
- `service`
- `repository`
- `resource`
- `sensitivity`

The graph/risk service may transform IR into graph nodes and edges, but it must
not change the IR field names without updating this contract and all consumers.
