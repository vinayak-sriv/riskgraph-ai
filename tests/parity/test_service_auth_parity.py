"""The three FastAPI services each ship their own copy of service_auth.py.

They are deliberate copies: every service builds from its own Docker context,
so a shared module would mean restructuring three build contexts for twenty
lines. The real risk is a security fix landing in two of the three -- this
asserts the logic stays identical, so the copies cannot silently diverge.
"""

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
COPIES = {
    "graph-risk-service": "RISKGRAPH_GRAPH_SERVICE_TOKEN",
    "ai-validation-service": "RISKGRAPH_AI_SERVICE_TOKEN",
    "python-analyzer": "RISKGRAPH_ANALYZER_SERVICE_TOKEN",
}


def _normalized(service: str, token_env: str) -> str:
    """Source with comments, blank lines and the per-service env var removed."""
    text = (ROOT / "services" / service / "app/service_auth.py").read_text(encoding="utf-8")
    text = text.replace(token_env, "SERVICE_TOKEN_ENV")
    lines = [
        line.rstrip()
        for line in text.splitlines()
        if line.strip() and not line.strip().startswith("#")
    ]
    return "\n".join(lines)


def test_all_three_service_auth_copies_are_logically_identical():
    normalized = {service: _normalized(service, token) for service, token in COPIES.items()}
    reference_service, reference = next(iter(normalized.items()))

    drifted = [s for s, text in normalized.items() if text != reference]

    assert not drifted, (
        f"service_auth.py in {drifted} differs from {reference_service}; "
        "apply auth changes to all three copies"
    )


def test_each_copy_reads_its_own_service_token():
    for service, token_env in COPIES.items():
        source = (ROOT / "services" / service / "app/service_auth.py").read_text(encoding="utf-8")
        assert token_env in source, f"{service} does not read {token_env}"


def test_the_shared_logic_still_fails_closed_and_compares_constant_time():
    """Cheap guard on the properties that matter, independent of formatting."""
    source = _normalized("graph-risk-service", COPIES["graph-risk-service"])

    assert "hmac.compare_digest" in source, "credential comparison must be constant time"
    assert re.search(r"status_code=503", source), "an unset secret must fail closed with 503"
    assert re.search(r"status_code=401", source), "a bad credential must return 401"
    assert 'encode("utf-8")' in source, "compare bytes, not str (non-ASCII raises TypeError)"
