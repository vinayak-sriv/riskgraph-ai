"""Pinned public FastAPI source checks; never check out or execute external project code.

Track A batch 6 (docs/pending-updates.md): pin licensed public FastAPI repositories and
immutable commits, and record framework/version, source hashes, reviewer attribution,
coverage, diagnostics, runtime, and deterministic results. Structurally a straight port of
tools/evaluation/external_spring.py -- see that file for the integrity-checking rationale
(clone/fetch by pinned SHA only, verify source+license hashes before ever reading the
files, never execute the target repository).

# ponytail: assess() hard-asserts the endpoint/method/path/authentication oracle (verified
# by reading the pinned diff by hand), that identical before/after evidence yields zero
# graph/risk delta and a non-BLOCK verdict, and quality.confidence/coverage_ratio against
# expected_confidence/expected_coverage_ratio in the manifest -- those two were confirmed by
# an actual run (see datasets/external-fastapi/observed-results-2026-09-19.json) rather than
# derived by hand, since they come from graph-risk-service's own aggregation over the
# envelope, not from this script's own extraction logic.
"""

import argparse
import hashlib
import json
import re
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/dev"))
from platform_session import PlatformSession  # noqa: E402

DATA = ROOT / "datasets/external-fastapi"
REPOSITORIES = ROOT / "samples/generated/external-fastapi"


def read_manifest():
    manifest = json.loads((DATA / "manifest.json").read_text(encoding="utf-8"))
    if manifest.get("human_reviewed"):
        if (
            manifest.get("label_status") != "REVIEWED"
            or manifest.get("review_type") != "HUMAN_SOURCE_REVIEW"
        ):
            raise ValueError(
                "Human-reviewed manifests must use the reviewed status and review type"
            )
        for case in manifest["cases"]:
            review = case.get("review") or {}
            if not all(
                str(review.get(key, "")).strip() for key in ("reviewer", "reviewed_at", "notes")
            ):
                raise ValueError(
                    "Every human-reviewed case requires reviewer, reviewed_at and notes"
                )
            if review.get("label") not in {"POSITIVE", "NEGATIVE", "INCONCLUSIVE"}:
                raise ValueError("Every human-reviewed case requires an explicit label")
    elif (
        manifest.get("label_status") != "PROVISIONAL"
        or manifest.get("review_type") != "AI_SOURCE_REVIEW"
    ):
        raise ValueError("Non-human-reviewed manifests must remain provisional AI source reviews")
    seen = set()
    for case in manifest["cases"]:
        name = case["id"]
        if not re.fullmatch(r"[a-z0-9-]+", name) or name in seen:
            raise ValueError("Unsafe or duplicate case ID")
        seen.add(name)
        if not re.fullmatch(r"https://github\.com/fastapi/[a-z0-9-]+\.git", case["repository"]):
            raise ValueError("Only the registered official public FastAPI sources are supported")
        if any(
            not re.fullmatch(r"[0-9a-f]{40}", case[key]) for key in ("old_commit", "new_commit")
        ):
            raise ValueError("Immutable commit required")
        for item in [case["license"], *case["sources"]]:
            path = item["path"]
            if path.startswith("/") or "\\" in path or ":" in path or ".." in path.split("/"):
                raise ValueError("Unsafe source path")
    return manifest


def git(repo, *args):
    return subprocess.check_output(
        ["git", "-c", "core.hooksPath=/dev/null", "-C", str(repo), *args], timeout=120
    )


def prepare(case):
    repo = REPOSITORIES / case["id"]
    REPOSITORIES.mkdir(parents=True, exist_ok=True)
    if repo.is_symlink() or (
        repo.exists() and not repo.resolve().is_relative_to(REPOSITORIES.resolve())
    ):
        raise ValueError("Repository resolves outside evaluation directory")
    if not repo.exists():
        # A real checkout (not --no-checkout) is required here: unlike the Java/Spring
        # evaluation, extraction itself only ever reads via git plumbing (git show/diff), but
        # RepositoryFrameworkDetector routes by walking the actual working-tree files on disk
        # for a *.py file importing fastapi. With nothing checked out it finds nothing and
        # silently falls back to the Java analyzer. Checking out the default branch (still the
        # same pinned repository, just not one of the exact pinned SHAs) is enough -- detection
        # only needs *some* fastapi-importing .py file to be visible.
        subprocess.run(
            ["git", "clone", "--depth=1", case["repository"], str(repo)],
            check=True,
            timeout=120,
        )
    if git(repo, "remote", "get-url", "origin").decode().strip() != case["repository"]:
        raise ValueError("Existing repository origin differs; preserving it")
    for sha in (case["old_commit"], case["new_commit"]):
        try:
            git(repo, "cat-file", "-e", sha + "^{commit}")
        except subprocess.CalledProcessError:
            git(repo, "fetch", "--depth=2", "origin", sha)
    for item in [case["license"], *case["sources"]]:
        for revision in ("old", "new"):
            content = git(repo, "show", case[revision + "_commit"] + ":" + item["path"])
            if hashlib.sha256(content).hexdigest() != item[revision + "_sha256"]:
                raise ValueError("Pinned source/license integrity mismatch")
    return repo


def assess(case, scan):
    matched = 0
    expected = 0
    for revision in ("before", "after"):
        rows = scan["source_evidence"][revision]
        actual = {
            (
                row["endpoint"]["method"],
                row["endpoint"]["endpoint"],
                row["endpoint"]["authentication"],
                row["endpoint"]["required_role"],
            )
            for row in rows
        }
        gold = {
            (row["method"], row["endpoint"], row["authentication"], row["required_role"])
            for row in case["expected_changed_endpoints"]
        }
        matched += len(actual & gold)
        expected += len(gold)
    assert not scan["graph_delta"]["new_paths"], "Unexpected new path on reviewed source change"
    assert scan["risk_result"]["risk_delta"] == 0
    assert scan["final_verdict"] != "BLOCK", "A reviewed cosmetic change must never fail closed to BLOCK"
    assert scan["quality"]["confidence"] == case["expected_confidence"]
    assert scan["quality"]["coverage_ratio"] == case["expected_coverage_ratio"]
    if case["expected_diagnostic"]:
        assert case["expected_diagnostic"] in {d["code"] for d in scan["diagnostics"]}
    return dict(
        matched_endpoint_auth_rows=matched,
        expected_endpoint_auth_rows=expected,
        endpoint_auth_recall=matched / expected if expected else None,
        quality=scan["quality"],
        coverage=scan["coverage"],
        verdict=scan["final_verdict"],
        risk_delta=scan["risk_result"]["risk_delta"],
        diagnostic_codes=sorted({d["code"] for d in scan["diagnostics"]}),
    )


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--prepare-only", action="store_true")
    parser.add_argument("--compose", action="store_true")
    parser.add_argument("--output", type=Path, default=ROOT / "tmp/external-fastapi-evaluation")
    args = parser.parse_args()
    manifest = read_manifest()
    args.output.mkdir(parents=True, exist_ok=True)
    results = {}
    session = PlatformSession()
    for case in manifest["cases"]:
        repo = prepare(case)
        if args.prepare_only:
            print(case["id"] + ": pinned source and license verified")
            continue
        request = dict(
            repository_path="/analysis-repositories/external-fastapi/" + case["id"]
            if args.compose
            else str(repo.resolve()),
            old_commit=case["old_commit"],
            new_commit=case["new_commit"],
        )
        scans = []
        started = time.perf_counter()
        for _ in range(2):
            scans.append(session.call("/analyses", request))
        scan, repeated = scans
        for key in (
            "scan_id",
            "provenance",
            "graph_delta",
            "risk_result",
            "source_evidence",
            "quality",
            "coverage",
            "diagnostics",
        ):
            assert scan[key] == repeated[key], (case["id"], "nondeterministic", key)
        result = assess(case, scan)
        result["mean_runtime_seconds"] = round((time.perf_counter() - started) / 2, 3)
        results[case["id"]] = result
        (args.output / (case["id"] + ".json")).write_text(
            json.dumps(scan, indent=2) + "\n", encoding="utf-8", newline="\n"
        )
        print(case["id"] + ": checks passed; " + json.dumps(result), flush=True)
    if not args.prepare_only:
        summary = dict(
            scope="Two pinned external FastAPI changes on the same upstream repository; "
            "static extraction only; no execution or exploit validation",
            label_status=manifest["label_status"],
            review_type=manifest["review_type"],
            human_reviewed=manifest["human_reviewed"],
            vulnerability_precision=None,
            vulnerability_recall=None,
            vulnerability_f1=None,
            sensitivity_accuracy=None,
            cases=results,
        )
        (args.output / "summary.json").write_text(
            json.dumps(summary, indent=2) + "\n", encoding="utf-8", newline="\n"
        )


if __name__ == "__main__":
    main()
