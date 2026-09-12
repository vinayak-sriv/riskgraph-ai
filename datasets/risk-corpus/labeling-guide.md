# Provisional labeling guide

Safety is determined from source/auth/IR expectations, never from LLM verdicts.
Authorization removal, a new public sensitive endpoint, and public sensitive resource
exposure are positive structural examples. Protected refactors, protected additions,
cosmetic changes, empty changes, and authorization strengthening are negative examples.
Ambiguous and malformed cases require REVIEW and must not be recorded as ALLOW.

Weights are .25/.20/.20/.15/.10/.10 in the order declared in AGENTS.md. Sensitivity
LOW/MODERATE/MEDIUM/HIGH/CRITICAL maps to 20/40/60/80/100. Anonymous sensitive
reachability and exposure score 100; auth removal scores 100 and privilege impact 60.
Sensitive GET exploitability is 20 when protected, 50 when public; other methods 0.
Round the weighted sum to the nearest integer (Python ties-to-even).

BLOCK requires a new path and risk_after >=61. REVIEW applies at risk_after >=41,
delta >=21, or insufficient extraction. Other cases ALLOW. NOT_RUN preserves the
preliminary result. A label reviewer must inspect both sources, IR, paths, scores,
and provenance before any record can claim human review in a future schema version.
