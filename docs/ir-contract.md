# IR Contract

The intermediate representation is the stable contract between source analysis
and downstream graph/risk logic. Downstream services consume IR JSON only, never
raw Java source code.

The canonical schema is:

```text
contracts/ir/endpoint-ir.schema.json
```

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
