"""Builds the analysis envelope for a Python/FastAPI repository.

Track A batch 3 (docs/pending-updates.md): route/handler/auth-dependency
extraction via `extractor.py`. Batch 4 adds handler -> service/repository ->
resource resolution and sensitivity classification (extractor.py wires in
resolver.py and sensitivity_policy.py; see their docstrings for the exact
heuristic scope). This keeps the analyzer boundary honest end to end:
platform-api routing a FastAPI repository here instead of java-analyzer, and
the rest of the pipeline (graph/risk, decision, dashboard) running unchanged
on the result.
"""

import hashlib
import os
import subprocess
from datetime import UTC, datetime

from .extractor import build_dependency_alias_catalog, extract_endpoints
from .resolver import build_function_catalog
from .routing import resolve_router_prefixes

SCHEMA_VERSION = "1.1.0"
ANALYZER_VERSION = "0.3.0-batch5"
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
            "code": "PYTHON_RESOLUTION_IS_HEURISTIC",
            "message": (
                "Handler -> service/repository -> resource resolution matches a handler's own "
                "direct calls by naming convention (*_repository/*_service objects, SQLAlchemy "
                "query/get/add/select calls on session-like objects). Router mount prefixes are "
                "resolved across explicit imports; dynamic router factories and cross-file "
                "service calls remain unresolved."
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
            "endpoints_with_service": _count_resolved(before + after, "service"),
            "endpoints_with_repository": _count_resolved(before + after, "repository"),
            "coverage_ratio": _coverage_ratio(before + after),
        },
        "diagnostics": diagnostics,
    }


def _count_resolved(evidence: list[dict], field: str) -> int:
    return sum(1 for entry in evidence if entry["endpoint"][field] is not None)


def _coverage_ratio(evidence: list[dict]) -> float:
    # "endpoints with any resolved dependency / endpoints found" (bounded
    # 0..1), not "endpoints found / files" -- the latter can exceed 1
    # whenever a single file defines more than one route.
    if not evidence:
        return 0.0
    resolved = sum(
        1
        for entry in evidence
        if entry["endpoint"]["service"] is not None or entry["endpoint"]["repository"] is not None
    )
    return resolved / len(evidence)


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
    sources = _all_python_sources(repository_path, commit)
    prefixes = resolve_router_prefixes(sources)
    functions = build_function_catalog(sources)
    dependency_aliases = build_dependency_alias_catalog(sources)
    for entry in changed_files:
        path = entry[path_key]
        if path is None:
            continue
        source = sources.get(path)
        if source is None:
            source = _read_file_at_commit(repository_path, commit, path)
        if source is None:
            continue
        try:
            evidence.extend(
                extract_endpoints(source, path, prefixes.get(path), functions, dependency_aliases)
            )
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


def _all_python_sources(repository_path: str, commit: str) -> dict[str, str]:
    """Read tracked Python files at a commit without checking it out."""
    try:
        listing = subprocess.run(  # noqa: S603
            _git_command(repository_path, "ls-tree", "-r", "--name-only", commit),
            capture_output=True,
            text=True,
            timeout=30,
            check=True,
        )
    except (subprocess.SubprocessError, OSError):
        return {}
    sources: dict[str, str] = {}
    for path in (value for value in listing.stdout.splitlines() if value.endswith(".py")):
        source = _read_file_at_commit(repository_path, commit, path)
        if source is not None:
            sources[path] = source
    return sources


def _read_file_at_commit(repository_path: str, commit: str, path: str) -> str | None:
    """Non-mutating file-at-commit read via `git show`, matching the diff
    command below -- never checks out the commit, so it's safe to call
    concurrently with other scans against the same working copy."""
    try:
        # S603/S607: argv list, never a shell. repository_path is allowlisted by
        # repository_access.validate_repository_path and commit is a validated
        # 40-hex SHA. `git` resolves from PATH because its location differs per
        # platform and the container image pins the binary.
        result = subprocess.run(  # noqa: S603
            _git_command(repository_path, "show", f"{commit}:{path}"),
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
        # S603/S607: see _read_file_at_commit -- same argv-list, allowlisted-path
        # and validated-SHA guarantees.
        result = subprocess.run(  # noqa: S603
            _git_command(
                repository_path,
                "diff",
                "--name-status",
                old_commit,
                new_commit,
                "--",
                "*.py",
            ),
            capture_output=True,
            text=True,
            timeout=30,
            check=True,
        )
    except (subprocess.SubprocessError, OSError):
        return None
    changed: list[dict] = []
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


def _git_command(repository_path: str, *arguments: str) -> list[str]:
    """Trust only the already allowlisted repository for bind-mounted scans.

    Docker Desktop presents Windows bind mounts with an owner that differs from
    the non-root analyzer user. A command-local safe.directory avoids mutable
    global Git configuration while keeping every other repository untrusted.
    """
    return [
        "git",
        "-c",
        f"safe.directory={repository_path}",
        "-C",
        repository_path,
        *arguments,
    ]
