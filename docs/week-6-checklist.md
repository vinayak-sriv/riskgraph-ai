# Week 6 Call and Resource Extraction

Status: **Complete**

## Delivered

- [x] Resolve controller calls to Spring-style service types.
- [x] Traverse invoked service methods to repository types.
- [x] Avoid unrelated overloads by matching method name and argument count.
- [x] Derive the resource from the resolved repository/service/controller name.
- [x] Assign deterministic MVP sensitivity from a versioned YAML policy with an explicit fallback.
- [x] Emit the unchanged endpoint IR inside the versioned analysis envelope.
- [x] Report extraction coverage independently from risk.
- [x] Generate a deterministic two-commit authorization-removal repository.
- [x] Verify the complete Weeks 4–6 increment in CI.
- [x] Scope extraction to changed method and annotation ranges.
- [x] Propagate service-only changes to affected controller endpoints.
- [x] Preserve multiple service/repository paths outside the canonical endpoint IR.
- [x] Support `hasAuthority`, `@Secured`, and `@RolesAllowed` within annotation scope.
- [x] Report route, authorization, call-resolution, and overall confidence.
- [x] Enforce Java file-count, individual-size, total-size, and analysis-time limits.
- [x] Reject Java symlinks, unsupported Git entries, and Git LFS pointer sources.
- [x] Generate JaCoCo coverage and enforce analyzer module boundaries in tests.

## Verification

```powershell
python tools/dev/verify_week6.py
```

The generated sample is intentionally minimal source rather than a runnable target
application. Runtime sandbox validation remains Week 10 work.

The analyzer intentionally stays in Spoon no-classpath mode. It never invokes Maven,
Gradle, annotation processors, plugins, or code from the target repository. Ambiguous
resolution is retained as evidence with LOW confidence instead of being guessed.
