"""Verify the running Compose prototype after verify_mvp.py --compose --validation.

Restarts only the named project's platform, preserves its database, and checks
retrieval, normalized statuses, CORS, malformed requests, reporting and cleanup.
"""

import argparse
import json
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

from jsonschema.validators import validator_for
from platform_session import PlatformSession

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--project", default="riskgraph-mvp")
    args = parser.parse_args()
    bundle = ROOT / "tmp/evidence-bundle"
    scan = json.loads((bundle / "validated.json").read_text())
    "http://localhost:8080/analyses/" + scan["scan_id"]
    session = PlatformSession()

    def fetch():
        return session.call("/analyses/" + scan["scan_id"])

    before = fetch()
    assert before["validation_status"] == "CONFIRMED"
    compose = [
        "docker",
        "compose",
        "-p",
        args.project,
        "-f",
        str(ROOT / "infrastructure/docker-compose.yml"),
        "-f",
        str(ROOT / "infrastructure/docker-compose.validation.yml"),
    ]
    subprocess.run(
        compose + ["restart", "platform-api"], check=True, timeout=30, capture_output=True
    )
    deadline = time.monotonic() + 45
    while True:
        try:
            after = fetch()
            break
        except OSError:
            if time.monotonic() > deadline:
                raise
            time.sleep(0.5)
    assert before == after, "Stored result changed across restart"
    checks = dict(postgres_restart_roundtrip=True, scan_id=scan["scan_id"])
    query = "SELECT count(*) FROM scans s JOIN findings f ON f.scan_id=s.id JOIN validation_tests v ON v.finding_id=f.id WHERE s.validation_status<>v.status"
    count = subprocess.run(
        compose
        + ["exec", "-T", "postgres", "psql", "-U", "riskgraph", "-d", "riskgraph", "-tAc", query],
        check=True,
        timeout=10,
        capture_output=True,
        text=True,
    ).stdout.strip()
    assert count == "0", "Normalized validation status disagrees with scan"
    checks["normalized_validation_consistency"] = True
    for origin, allowed in [("http://localhost:5173", True), ("http://example.com", False)]:
        request = urllib.request.Request(
            "http://localhost:8080/analyses",
            method="OPTIONS",
            headers={
                "Origin": origin,
                "Access-Control-Request-Method": "POST",
                "Access-Control-Request-Headers": "content-type",
            },
        )
        try:
            response = urllib.request.urlopen(request, timeout=5)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            assert (response.headers.get("Access-Control-Allow-Origin") == origin) == allowed
    checks["cors_allowlist"] = True
    try:
        session.call("/analyses", dict(repository_path="..", old_commit="bad", new_commit="bad"))
        raise AssertionError("Malformed input accepted")
    except RuntimeError as error:
        detail = str(error)
        assert "HTTP 400" in detail
        assert '"final_verdict":"REVIEW"' in detail
        assert '"code":"INVALID_REQUEST"' in detail
    checks["malformed_request_fails_closed"] = True
    report_output = ROOT / "tmp/github-event"
    subprocess.run(
        [
            sys.executable,
            str(ROOT / "tools/reporting/submit_event.py"),
            str(ROOT / "contracts/github/examples/pull-request.json"),
            str(ROOT / "samples/generated/mvp-v2/authorization-removal"),
            "--container-repository",
            "/analysis-repositories/mvp-v2/authorization-removal",
            "--output",
            str(report_output),
        ],
        check=True,
        timeout=120,
        capture_output=True,
    )
    schema = json.loads((ROOT / "contracts/reporting/sarif-2.1.0.schema.json").read_text())
    validator_for(schema)(schema).validate(
        json.loads((report_output / "results.sarif").read_text())
    )
    checks["github_event_and_sarif"] = True
    for command in (
        ["ps", "-aq", "--filter", "label=ai.riskgraph.run"],
        ["network", "ls", "-q", "--filter", "label=ai.riskgraph.run"],
    ):
        result = subprocess.run(
            compose + ["exec", "-T", "validation-docker", "docker", *command],
            check=True,
            timeout=10,
            capture_output=True,
            text=True,
        )
        assert not result.stdout.strip(), (
            "A RiskGraph validation resource was not cleaned up (or a probe is still running)"
        )
    checks["sandbox_resources_cleaned_up"] = True
    (bundle / "runtime-checks.json").write_text(
        json.dumps(checks, indent=2) + "\n", encoding="utf-8", newline="\n"
    )
    print(json.dumps(checks, indent=2))


if __name__ == "__main__":
    main()
