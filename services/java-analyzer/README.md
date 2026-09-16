# Java analyzer

The Java analyzer performs bounded Git diff analysis and Spoon-based extraction for
Spring Boot applications.

`POST /analyze` accepts an allowlisted local repository and two immutable full commit
SHAs. It emits versioned endpoint and evidence IR containing routes, annotation-based
authorization, controller/service/repository paths, resource sensitivity, source
locations, coverage, confidence, diagnostics, and provenance.

## Security boundaries

- Target build scripts and repository code are never executed.
- Source acquisition is read-only and constrained by file, byte, time, queue, and
  concurrency limits.
- Compose runs each analysis in a separate JVM process so deadline cancellation can
  terminate the parser and its descendants.
- Immutable snapshot and extraction caches are bounded and content/configuration
  keyed.
- The analyzer does not score risk, call AI, perform validation, or write to the
  platform database.

The MVP supports method-annotation authorization such as `@PreAuthorize`.
`SecurityFilterChain` semantics are deliberately outside the implemented scope.

## Verification

From the repository root:

```powershell
mvn -f services/java-analyzer/pom.xml clean verify
```

For local repository generation and API examples, see the
[runtime guide](../../docs/runtime-guide.md).
