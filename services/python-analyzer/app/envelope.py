"""Builds the analysis envelope for a Python/FastAPI repository.

Route/endpoint extraction (Track A batch 3 in docs/pending-updates.md — Python AST
evidence for routers, decorators, handlers, auth dependencies) is not implemented
yet. This returns a schema-valid envelope (contracts/ir/analysis-envelope.schema.json)
carrying genuine changed-file evidence but zero extracted endpoints, so the analyzer
boundary — platform-api routing a FastAPI repository here instead of java-analyzer,
and the rest of the pipeline (graph/risk, decision, dashboard) running unchanged on
the result — can be proven end-to-end ahead of the real extractor landing.
"""

import hashlib
import os
import subprocess
from datetime import UTC, datetime

SCHEMA_VERSION = "1.1.0"
ANALYZER_VERSION = "0.1.0-stub"
CONFIG_HASH = hashlib.sha256(ANALYZER_VERSION.encode()).hexdigest()

_STATUS_MAP = {"A": "ADD", "M": "MODIFY", "D": "DELETE"}


def build_stub_envelope(repository_path: str, old_commit: str, new_commit: str) -> dict:
    old_commit = old_commit.lower()
    new_commit = new_commit.lower()
    changed_files = _changed_python_files(repository_path, old_commit, new_commit)
    repository_identity = os.path.basename(repository_path.rstrip("/\\")) or repository_path
    analysis_id = hashlib.sha256(
        f"{repository_identity}:{old_commit}:{new_commit}:{CONFIG_HASH}".encode()
    ).hexdigest()
    diagnostics = [{
        "severity": "INFO",
        "code": "PYTHON_EXTRACTION_NOT_IMPLEMENTED",
        "message": "FastAPI route/endpoint extraction is not implemented yet; this scan reports zero endpoints.",
        "path": None,
    }]
    if changed_files is None:
        changed_files = []
        diagnostics.append({
            "severity": "WARNING",
            "code": "GIT_DIFF_UNAVAILABLE",
            "message": "Could not compute the changed Python file list for this commit pair.",
            "path": None,
        })
    return {
        "schema_version": SCHEMA_VERSION,
        "analyzer_version": ANALYZER_VERSION,
        "analyzer_config_hash": CONFIG_HASH,
        "analysis_id": analysis_id,
        "analyzed_at": datetime.now(UTC).isoformat(),
        "provenance": {
            "repository_path": repository_path,
            "repository_identity": repository_identity,
            "old_commit": old_commit,
            "new_commit": new_commit,
        },
        "changed_files": changed_files,
        "before": [],
        "after": [],
        "coverage": {
            # ponytail: the shared envelope schema's coverage fields are still named for
            # the Java analyzer (additionalProperties: false forbids renaming them here
            # without a schema version bump touching every consumer); java_files_considered
            # below is really "changed Python files considered". Revisit once Track A batch 3
            # gives this service real coverage semantics worth a dedicated field set.
            "java_files_considered": len(changed_files),
            "controllers_discovered": 0,
            "endpoints_emitted": 0,
            "endpoints_with_service": 0,
            "endpoints_with_repository": 0,
            "coverage_ratio": 0.0,
        },
        "diagnostics": diagnostics,
    }


def _changed_python_files(repository_path: str, old_commit: str, new_commit: str) -> list[dict] | None:
    try:
        result = subprocess.run(
            ["git", "-C", repository_path, "diff", "--name-status", old_commit, new_commit, "--", "*.py"],
            capture_output=True, text=True, timeout=30, check=True,
        )
    except (subprocess.SubprocessError, OSError):
        return None
    changed = []
    for line in result.stdout.splitlines():
        if not line.strip():
            continue
        parts = line.split("\t")
        status = _STATUS_MAP.get(parts[0][0], "MODIFY")
        old_path = parts[1] if status != "ADD" and len(parts) > 1 else None
        new_path = parts[-1] if status != "DELETE" else None
        changed.append({
            "status": status,
            "old_path": old_path,
            "new_path": new_path,
            "old_ranges": [],
            "new_ranges": [],
        })
    return changed
