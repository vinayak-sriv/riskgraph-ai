"""Builds the analysis envelope for a Python/FastAPI repository.

Track A batch 3 (docs/pending-updates.md): route/handler/auth-dependency
extraction via `extractor.py` is implemented. Call/resource resolution
(handler -> service -> repository/resource, batch 4) is not, so every
extracted endpoint reports resource="unresolved" with LOW call_resolution
confidence -- see extractor.py's module docstring for the exact gaps. This
keeps the analyzer boundary honest end to end: platform-api routing a
FastAPI repository here instead of java-analyzer, and the rest of the
pipeline (graph/risk, decision, dashboard) running unchanged on the result.
"""

import hashlib
import os
import subprocess
from datetime import UTC, datetime

from .extractor import extract_endpoints

SCHEMA_VERSION = "1.1.0"
ANALYZER_VERSION = "0.2.0-batch3"
CONFIG_HASH = hashlib.sha256(ANALYZER_VERSION.encode()).hexdigest()

_STATUS_MAP = {"A": "ADD", "M": "MODIFY", "D": "DELETE"}


def build_envelope(repository_path: str, old_commit: str, new_commit: str) -> dict:
    old_commit = old_commit.lower()
    new_commit = new_commit.lower()
    changed_files = _changed_python_files(repository_path, old_commit, new_commit)
    repository_identity = os.path.basename(repository_path.rstrip("/\\")) or repository_path
    analysis_id = hashlib.sha256(
        f"{repository_identity}:{old_commit}:{new_commit}:{CONFIG_HASH}".encode()
    ).hexdigest()
    diagnostics = [
        {
            "severity": "INFO",
            "code": "PYTHON_RESOURCE_RESOLUTION_NOT_IMPLEMENTED",
            "message": (
                "Route and authentication-dependency evidence is extracted, but handler -> "
                "service -> repository/resource resolution is not; every finding reports "
                'resource="unresolved" with LOW call_resolution confidence.'
            ),
            "path": None,
        }
    ]
    if changed_files is None:
        changed_files = []
        diagnostics.append(
            {
                "severity": "WARNING",
                "code": "GIT_DIFF_UNAVAILABLE",
                "message": "Could not compute the changed Python file list for this commit pair.",
                "path": None,
            }
        )

    before = _extract_side(
        repository_path, old_commit, changed_files, side="old", diagnostics=diagnostics
    )
    after = _extract_side(
        repository_path, new_commit, changed_files, side="new", diagnostics=diagnostics
    )

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
        "before": before,
        "after": after,
        "coverage": {
            # ponytail: the shared envelope schema's coverage fields are still named for
            # the Java analyzer (additionalProperties: false forbids renaming them here
            # without a schema version bump touching every consumer); java_files_considered
            # below is really "changed Python files considered".
            "java_files_considered": len(changed_files),
            "controllers_discovered": len({e["qualified_controller"] for e in before + after}),
            "endpoints_emitted": len(before) + len(after),
            # ponytail: batch 4 (call/resource resolution) is what makes these non-zero.
            # coverage_ratio is deliberately "endpoints with resolved deps / endpoints
            # found" (bounded 0..1), not "endpoints found / files" -- the latter can
            # exceed 1 whenever a single file defines more than one route.
            "endpoints_with_service": 0,
            "endpoints_with_repository": 0,
            "coverage_ratio": 0.0,
        },
        "diagnostics": diagnostics,
    }


def _extract_side(
    repository_path: str,
    commit: str,
    changed_files: list[dict],
    *,
    side: str,
    diagnostics: list[dict],
) -> list[dict]:
    path_key = "old_path" if side == "old" else "new_path"
    evidence = []
    for entry in changed_files:
        path = entry[path_key]
        if path is None:
            continue
        source = _read_file_at_commit(repository_path, commit, path)
        if source is None:
            continue
        try:
            evidence.extend(extract_endpoints(source, path))
        except Exception as exc:  # noqa: BLE001 -- one bad file must not fail the whole scan
            diagnostics.append(
                {
                    "severity": "WARNING",
                    "code": "PYTHON_EXTRACTION_FAILED",
                    "message": f"Could not extract routes from {path}: {exc}",
                    "path": path,
                }
            )
    return evidence


def _read_file_at_commit(repository_path: str, commit: str, path: str) -> str | None:
    """Non-mutating file-at-commit read via `git show`, matching the diff
    command below -- never checks out the commit, so it's safe to call
    concurrently with other scans against the same working copy."""
    try:
        result = subprocess.run(
            ["git", "-C", repository_path, "show", f"{commit}:{path}"],
            capture_output=True,
            text=True,
            timeout=30,
            check=True,
        )
    except (subprocess.SubprocessError, OSError):
        return None
    return result.stdout


def _changed_python_files(
    repository_path: str, old_commit: str, new_commit: str
) -> list[dict] | None:
    try:
        result = subprocess.run(
            [
                "git",
                "-C",
                repository_path,
                "diff",
                "--name-status",
                old_commit,
                new_commit,
                "--",
                "*.py",
            ],
            capture_output=True,
            text=True,
            timeout=30,
            check=True,
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
        changed.append(
            {
                "status": status,
                "old_path": old_path,
                "new_path": new_path,
                "old_ranges": [],
                "new_ranges": [],
            }
        )
    return changed
