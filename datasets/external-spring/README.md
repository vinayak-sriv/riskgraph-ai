# External Spring source checks

These two Apache-2.0 repositories are separate from the synthetic calibration
corpus. `manifest.json` pins immutable commit pairs and SHA-256 hashes for source
and license files in both revisions. Sources stay in ignored
`samples/generated/external`; their build scripts are never executed.

Labels are **PROVISIONAL**, based on AI source review, and **not human validated**.
They describe two negative changes, not a representative vulnerability benchmark.

| Source | Reviewed change | Expected source surface | Current limitation |
|---|---|---|---|
| [Spring Petclinic](https://github.com/spring-projects/spring-petclinic/commit/bb37aad8c332264723817d855e8b3b96b7c392bc) | Trim owner-search whitespace | GET `/owners`, no authorization annotation | Controller calls a helper; the resource dependency is unresolved |
| [Spring REST guide](https://github.com/spring-guides/gs-rest-service/commit/389429a7345a718c27a77cabc495cf7ea68bbaee) | Java indentation only | GET `/greeting`, no authorization annotation | No service/repository path exists, so dependency coverage remains incomplete |

The endpoint oracle comes from the source annotations, independently of analyzer
output. No annotation does not establish effective runtime access: security
configuration parsing is outside scope. Both scans must stay REVIEW because the
evidence is incomplete, with no new path or risk increase. Parser failures count
as missed expected endpoints. Unresolved resource paths count as zero coverage.

```powershell
python tools/evaluation/external_spring.py --prepare-only
python tools/evaluation/external_spring.py --compose
```

The second command requires the local Compose platform. It checks repeated
deterministic evidence, writes both scans and measured results under
`tmp/external-evaluation`, and never runs HTTP security tests on these repositories.
The latest checked-in automated summary is `observed-results-2026-09-15.json`.
The September 13 summary is retained as historical evidence of the pre-0.4.1 parser
failure. The September 15 current-analyzer platform rerun emits both `/greeting`
rows without `SPOON_MODEL_FAILED` and records two repeated deterministic executions
per case. A Compose rerun is not required to execute third-party code and must not
run HTTP security tests against these repositories.
Use `HUMAN_REVIEW.md` for the independent review step; manifest validation prevents
an unattributed `human_reviewed` claim.
Precision/recall/F1 for vulnerabilities and sensitivity accuracy are unset: two
negative, unconfirmed examples do not support those claims. Human review and a
broader positive/negative external corpus remain release gates.

Both pinned `LICENSE.txt` files are Apache License 2.0. Their exact license hashes
are recorded in the manifest and checked before analysis. No third-party source is
redistributed in this directory; upstream links preserve attribution and provenance.
