# Risk calibration corpus v1

100 deterministic synthetic records, 50% safe/negative. All labels are
**PROVISIONAL / SYNTHETIC_AI_ASSISTED**. No human review or real-world accuracy is claimed.

Run `python datasets/risk-corpus/tools/corpus.py generate`, then `validate`, then
`evaluate --output tmp/corpus-evaluation.json`. Generation uses an explicit independent
rubric, not the graph engine's observed outputs. Tests compare the implementation
against those provisional expectations. Do not tune policy to the held-out split.

Six repository groups are development (60 records), two calibration (20), two test
(20). Groups share broad scenario categories and rubric; they contain no shared
repository identity. This is a small parameterized behavioral suite, not a diverse
external evaluation benchmark. Source fragments are immutable by SHA-256; their
identities are explicitly not Git commits. They are not executable sandbox apps.

IR, full graph paths, both component vectors, scores, preliminary verdict, permissible
AI evidence, Docker expectations, NOT_RUN status, and final verdict are recorded.
Source extraction metrics are available through `extraction_metrics` when actual
Spoon results are supplied; the IR-only evaluation reports them as null.
