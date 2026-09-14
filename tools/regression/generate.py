"""Convert confirmed sandbox findings into deterministic graph/risk regressions."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path

SHA256_ID = re.compile(r"^sha256:[0-9a-f]{64}$")
COMMIT = re.compile(r"^[0-9a-f]{40}$")


def build_case(scan: dict) -> dict:
    validation = scan.get("validation") or {}
    provenance = scan.get("provenance") or {}
    risk = scan.get("risk_result") or {}
    if validation.get("status") != "CONFIRMED" or validation.get("confirmed") is not True:
        raise ValueError("Only a CONFIRMED sandbox validation can become a regression")
    if validation.get("cleanup_complete") is not True:
        raise ValueError("Validation cleanup must be complete")
    if not SHA256_ID.fullmatch(str(validation.get("container_image_id", ""))):
        raise ValueError("Validation must include an immutable container image ID")
    probe_image_id = validation.get("probe_image_id")
    if probe_image_id is not None and not SHA256_ID.fullmatch(str(probe_image_id)):
        raise ValueError("Validation probe image ID must be immutable when present")
    if not re.fullmatch(r"[0-9a-f]{64}", str(validation.get("response_sha256", ""))):
        raise ValueError("Validation must include a response SHA-256")
    new_commit = str(provenance.get("new_commit", ""))
    if not COMMIT.fullmatch(new_commit) or validation.get("source_commit") != new_commit:
        raise ValueError("Validation must be bound to the analyzed new commit")

    def endpoints(revision: str) -> list[dict]:
        rows = scan.get("source_evidence", {}).get(revision, [])
        return [row["endpoint"] for row in rows]

    case = {
        "schema_version": "1.0.0",
        "regression_id": f"confirmed:{scan['scan_id']}",
        "source": {
            "scan_id": scan["scan_id"],
            "repository_identity": provenance["repository_identity"],
            "old_commit": provenance["old_commit"],
            "new_commit": new_commit,
            "identity_provenance": (
                "LEGACY_COMMIT_DERIVED"
                if provenance["repository_identity"].endswith(provenance["old_commit"])
                else "STABLE_REPOSITORY"
            ),
        },
        "before": endpoints("before"),
        "after": endpoints("after"),
        "expected": {
            "risk_before": risk["risk_before"],
            "risk_after": risk["risk_after"],
            "risk_delta": risk["risk_delta"],
            "verdict": scan["final_verdict"],
            "new_paths": len(scan.get("graph_delta", {}).get("new_paths", [])),
        },
        "confirmation": {
            "status": validation["status"],
            "container_image_id": validation["container_image_id"],
            "probe_image_id": probe_image_id,
            "probe_provenance": ("COMPLETE" if probe_image_id else "LEGACY_MISSING"),
            "response_sha256": validation["response_sha256"],
            "source_commit": validation["source_commit"],
            "cleanup_complete": validation["cleanup_complete"],
        },
    }
    canonical = json.dumps(case, sort_keys=True, separators=(",", ":")).encode()
    case["content_sha256"] = hashlib.sha256(canonical).hexdigest()
    return case


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("scan", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    case = build_case(json.loads(args.scan.read_text(encoding="utf-8")))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(case, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(f"Wrote confirmed regression {case['regression_id']} to {args.output}")


if __name__ == "__main__":
    main()
