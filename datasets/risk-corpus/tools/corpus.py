"""Reproducible provisional calibration corpus with an independent policy oracle.

No labels are obtained from the implementation under evaluation or an LLM call.
The authoring assistant's scenario labels remain PROVISIONAL.
"""

import argparse
import hashlib
import json
import math
import statistics
import sys
import time
from pathlib import Path
from typing import Literal
from urllib.parse import quote

from jsonschema import Draft202012Validator
from pydantic import BaseModel, ConfigDict, Field

CORPUS = Path(__file__).resolve().parents[1]
ROOT = CORPUS.parents[1]
sys.path.insert(0, str(ROOT / "services/graph-risk-service"))
from app.models import EndpointIr, Quality  # noqa: E402

WEIGHTS = [0.25, 0.20, 0.20, 0.15, 0.10, 0.10]
COMPONENTS = [
    "reachability",
    "authorization_change",
    "data_sensitivity",
    "external_exposure",
    "privilege_impact",
    "exploitability",
]
KINDS = [
    "authorization-removal",
    "new-public-sensitive-endpoint",
    "sensitive-resource-exposure",
    "authorization-strengthening",
    "safe-cosmetic",
    "safe-refactoring",
    "safe-protected-addition",
    "safe-empty-change",
    "ambiguous",
    "malformed",
]


class Strict(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)


class Provenance(Strict):
    repository_family: str
    scenario_family: str
    before_source: str
    after_source: str
    before_sha256: str = Field(pattern=r"^[0-9a-f]{64}$")
    after_sha256: str = Field(pattern=r"^[0-9a-f]{64}$")
    diff_sha256: str = Field(pattern=r"^[0-9a-f]{64}$")
    commit_identity: Literal["NOT_A_GIT_COMMIT_SYNTHETIC_SOURCE"]
    analyzer_version: Literal["expected-spoon-contract-v1"]


class RiskExpectation(Strict):
    components_before: list[int] = Field(min_length=6, max_length=6)
    components_after: list[int] = Field(min_length=6, max_length=6)
    risk_before: int = Field(ge=0, le=100)
    risk_after: int = Field(ge=0, le=100)
    risk_delta: int = Field(ge=-100, le=100)
    category_before: str
    category_after: str


class Expected(Strict):
    before_ir: list[EndpointIr]
    after_ir: list[EndpointIr]
    added_paths: list[list[str]]
    removed_paths: list[list[str]]
    risk: RiskExpectation
    preliminary_verdict: Literal["ALLOW", "REVIEW", "BLOCK"]
    ai_permitted_evidence: list[str]
    validation_target: Literal["LOCAL_DOCKER_ONLY"]
    validation_status: Literal["NOT_RUN"]
    validation_expectation: Literal["VULNERABLE_RESPONSE", "PROTECTED_RESPONSE", "NOT_APPLICABLE"]
    final_verdict: Literal["ALLOW", "REVIEW", "BLOCK"]


class Record(Strict):
    schema_version: Literal["1.0.0"]
    id: str = Field(pattern=r"^rg-[0-9a-f]{20}$")
    label_status: Literal["PROVISIONAL"]
    source_type: Literal["SYNTHETIC_AI_ASSISTED"]
    split: Literal["development", "calibration", "test"]
    scenario: str
    safe_negative: bool
    provenance: Provenance
    quality: Quality
    expected: Expected


def encoded(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True).encode()


def sha(value):
    return hashlib.sha256(value).hexdigest()


def category(score):
    return ["LOW", "MODERATE", "MEDIUM", "HIGH", "CRITICAL"][min(4, max(0, (score - 1) // 20))]


def total(values):
    return round(sum(value * weight for value, weight in zip(values, WEIGHTS, strict=True)))


def oracle_scores(rows, removed=False):
    sensitivity = max(
        (
            {"LOW": 20, "MODERATE": 40, "MEDIUM": 60, "HIGH": 80, "CRITICAL": 100}[
                row["sensitivity"]
            ]
            for row in rows
        ),
        default=0,
    )
    public = any(
        not r["authentication"] and r["sensitivity"] in ("MEDIUM", "HIGH", "CRITICAL") for r in rows
    )
    get = any(
        r["method"] == "GET" and r["sensitivity"] in ("MEDIUM", "HIGH", "CRITICAL") for r in rows
    )
    return [
        100 if public else 0,
        100 if removed else 0,
        sensitivity,
        100 if public else 0,
        60 if removed else 0,
        50 if public and get else 20 if get else 0,
    ]


def affected_rows(rows, added_paths, *, match_resource=True):
    """Return route state for the finding without mixing unrelated endpoints."""
    affected = {(path[1], path[-1]) for path in added_paths if len(path) > 1}
    affected_routes = {route for route, _ in affected}
    return [
        row
        for row in rows
        if f"endpoint:{row['method']}:{row['endpoint']}" in affected_routes
        and (
            not match_resource
            or (
                f"endpoint:{row['method']}:{row['endpoint']}",
                f"resource:{row['resource']}",
            )
            in affected
        )
    ]


def coherent_scores(rows, removed=False):
    """Select the highest complete route state, matching the runtime scorer."""
    candidates = [oracle_scores([row], removed) for row in rows]
    return max(candidates, key=total) if candidates else oracle_scores([], removed)


def expected_paths(rows):
    paths = []
    for row in rows:
        if row["authentication"] or row["sensitivity"] not in ("MEDIUM", "HIGH", "CRITICAL"):
            continue
        context = ":".join(
            quote(str(row[key] or ""), safe="")
            for key in ("method", "endpoint", "controller", "service", "repository", "resource")
        )
        path = ["user:anonymous", f"endpoint:{row['method']}:{row['endpoint']}"]
        for key in ("controller", "service", "repository"):
            if row[key]:
                path.append(f"{key}:{quote(row[key], safe='')}@{context}")
        path.append("resource:" + row["resource"])
        paths.append(path)
    return sorted(paths)


def source(rows, kind, after, name):
    if kind == "malformed" and after:
        return f"class {name} {{ !!!\n"
    if not rows:
        return f"class {name} {{ /* No changed endpoints. */ }}\n"
    # These source fragments are parser fixtures, not runnable apps or real commits.
    out = []
    for row in rows:
        auth = "@PreAuthorize(\"hasRole('ADMIN')\")" if row["authentication"] else ""
        if kind == "ambiguous":
            auth = '@PreAuthorize("authentication.name == #owner")'
        authorization = f" {auth}" if auth else ""
        out.append(f'''@RestController class {row["controller"]} {{
    {row["service"]} service;
    @{row["method"].title()}Mapping("{row["endpoint"]}"){authorization}
    Object read() {{ return service.read(); }}
}}
class {row["service"]} {{ {row["repository"]} repository; Object read() {{ return repository.findAll(); }} }}
interface {row["repository"]} {{ Object findAll(); }}
''')
    if kind == "safe-cosmetic" and after:
        out.append("// Changed documentation formatting only.\n")
    return "\n".join(out)


def generate():
    import difflib

    records = []
    for family in range(10):
        split = "development" if family < 6 else "calibration" if family < 8 else "test"
        for kind in KINDS:
            # Repository groups share no source files or record IDs across splits.
            name = f"Family{family}{kind.title().replace('-', '')}"
            row = dict(
                endpoint=f"/family-{family}/{kind}",
                method="GET" if family % 2 == 0 else "POST",
                controller=name + "Controller",
                authentication=True,
                required_role="ADMIN",
                service=name + "Service",
                repository="CustomerRepository" if family % 3 else "PaymentRepository",
                resource="Customer" if family % 3 else "Payment",
                sensitivity="HIGH" if family % 3 else "CRITICAL",
            )
            public = {**row, "authentication": False, "required_role": None}
            before, after = [row], [row.copy()]
            quality = dict(confidence="HIGH", coverage_ratio=1.0, incomplete=False)
            if kind == "authorization-removal":
                after = [public]
            if kind == "new-public-sensitive-endpoint":
                before, after = [], [public]
            if kind == "sensitive-resource-exposure":
                before, after = (
                    [
                        {
                            **public,
                            "resource": "Catalog",
                            "sensitivity": "LOW",
                            "repository": "CatalogRepository",
                        }
                    ],
                    [public],
                )
            if kind == "authorization-strengthening":
                before = [public]
            if kind == "safe-refactoring":
                after = [{**row, "service": name + "ReadService"}]
            if kind == "safe-protected-addition":
                before = []
            if kind == "safe-empty-change":
                before, after = [], []
            if kind in ("ambiguous", "malformed"):
                quality = dict(confidence="LOW", coverage_ratio=0.0, incomplete=True)
                if kind == "malformed":
                    before, after = [], []
                else:
                    before = [{**row, "required_role": None}]
                    after = [before[0].copy()]
            before_paths, after_paths = expected_paths(before), expected_paths(after)
            before_by_route = {(path[1], path[-1]): path for path in before_paths}
            after_by_route = {(path[1], path[-1]): path for path in after_paths}
            added = [
                after_by_route[key]
                for key in sorted(after_by_route.keys() - before_by_route.keys())
            ]
            removed = [
                before_by_route[key]
                for key in sorted(before_by_route.keys() - after_by_route.keys())
            ]
            # risk_before is repository-wide in both branches; the per-route
            # before-state belongs to the finding, not the scan summary.
            before_rows = before
            after_rows = affected_rows(after, added) if added else after
            b = coherent_scores(before_rows)
            a = coherent_scores(after_rows, kind == "authorization-removal")
            rb, ra = total(b), total(a)
            verdict = (
                "BLOCK"
                if added and ra >= 61
                else "REVIEW"
                if ra >= 41 or ra - rb >= 21 or quality["incomplete"]
                else "ALLOW"
            )
            identity = f"family-{family}/{kind}"
            before_source, after_source = (
                source(before, kind, False, name),
                source(after, kind, True, name),
            )
            before_file, after_file = (
                f"fixtures/{identity}/{state}.java" for state in ("before", "after")
            )
            for path, content in [(before_file, before_source), (after_file, after_source)]:
                target = CORPUS / path
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(content, encoding="utf-8", newline="\n")
            diff = "".join(
                difflib.unified_diff(before_source.splitlines(True), after_source.splitlines(True))
            )
            record = dict(
                schema_version="1.0.0",
                id="rg-" + sha(identity.encode())[:20],
                label_status="PROVISIONAL",
                source_type="SYNTHETIC_AI_ASSISTED",
                split=split,
                scenario=kind,
                safe_negative=kind.startswith("safe-") or kind == "authorization-strengthening",
                quality=quality,
                provenance=dict(
                    repository_family=f"family-{family}",
                    scenario_family=identity,
                    before_source=before_file,
                    after_source=after_file,
                    before_sha256=sha(before_source.encode()),
                    after_sha256=sha(after_source.encode()),
                    diff_sha256=sha(diff.encode()),
                    commit_identity="NOT_A_GIT_COMMIT_SYNTHETIC_SOURCE",
                    analyzer_version="expected-spoon-contract-v1",
                ),
                expected=dict(
                    before_ir=before,
                    after_ir=after,
                    added_paths=added,
                    removed_paths=removed,
                    risk=dict(
                        components_before=b,
                        components_after=a,
                        risk_before=rb,
                        risk_after=ra,
                        risk_delta=ra - rb,
                        category_before=category(rb),
                        category_after=category(ra),
                    ),
                    preliminary_verdict=verdict,
                    ai_permitted_evidence=[
                        "Only deterministic route/authentication/path evidence may be explained"
                    ],
                    validation_target="LOCAL_DOCKER_ONLY",
                    validation_status="NOT_RUN",
                    validation_expectation="VULNERABLE_RESPONSE"
                    if kind in KINDS[:3]
                    else "PROTECTED_RESPONSE"
                    if before or after
                    else "NOT_APPLICABLE",
                    final_verdict=verdict,
                ),
            )
            Record.model_validate(record)
            records.append(record)
    for split in ("development", "calibration", "test"):
        data = "".join(
            json.dumps(record, sort_keys=True) + "\n"
            for record in records
            if record["split"] == split
        )
        (CORPUS / f"{split}.jsonl").write_text(data, encoding="utf-8", newline="\n")
    schema = Record.model_json_schema()
    schema["$schema"] = "https://json-schema.org/draft/2020-12/schema"
    (CORPUS / "dataset.schema.json").write_text(
        json.dumps(schema, indent=2) + "\n", encoding="utf-8", newline="\n"
    )
    (CORPUS / "policy-v1.yml").write_bytes(
        (ROOT / "services/graph-risk-service/app/policy-v1.yml").read_bytes()
    )
    paths = (
        list(CORPUS.glob("*.jsonl"))
        + list((CORPUS / "fixtures").rglob("*.java"))
        + [CORPUS / "dataset.schema.json", CORPUS / "policy-v1.yml"]
    )
    manifest = dict(
        version="1.0.0",
        label_status="PROVISIONAL",
        count=len(records),
        split_counts=dict(development=60, calibration=20, test=20),
        safe_negative_fraction=sum(r["safe_negative"] for r in records) / len(records),
        files={
            path.relative_to(CORPUS).as_posix(): sha(path.read_bytes()) for path in sorted(paths)
        },
    )
    (CORPUS / "manifest.json").write_text(
        json.dumps(manifest, indent=2) + "\n", encoding="utf-8", newline="\n"
    )
    return records


def content_fingerprint(record):
    # IDs, family labels, filenames and split names cannot hide identical examples.
    return sha(
        encoded(
            [
                record["expected"]["before_ir"],
                record["expected"]["after_ir"],
                record["provenance"]["before_sha256"],
                record["provenance"]["after_sha256"],
            ]
        )
    )


def validate():
    import difflib

    manifest = json.loads((CORPUS / "manifest.json").read_text())
    schema = json.loads((CORPUS / "dataset.schema.json").read_text())
    Draft202012Validator.check_schema(schema)
    for name, digest in manifest["files"].items():
        assert sha((CORPUS / name).read_bytes()) == digest, f"Integrity mismatch: {name}"
    records, ids, payloads, families = [], set(), set(), {}
    for split in ("development", "calibration", "test"):
        values = [json.loads(line) for line in (CORPUS / f"{split}.jsonl").read_text().splitlines()]
        assert len(values) == manifest["split_counts"][split]
        for record in values:
            Draft202012Validator(schema).validate(record)
            Record.model_validate(record)
            assert record["id"] not in ids
            ids.add(record["id"])
            identity = record["provenance"]["scenario_family"]
            assert record["id"] == "rg-" + sha(identity.encode())[:20]
            assert record["split"] == split
            family = record["provenance"]["repository_family"]
            assert families.setdefault(family, split) == split, "Repository-family split leakage"
            content = content_fingerprint(record)
            assert content not in payloads
            payloads.add(content)
            source_text = []
            for revision in ("before", "after"):
                path = (CORPUS / record["provenance"][revision + "_source"]).resolve()
                assert path.is_relative_to(CORPUS.resolve())
                assert sha(path.read_bytes()) == record["provenance"][revision + "_sha256"]
                source_text.append(path.read_text())
            diff = "".join(difflib.unified_diff(*(text.splitlines(True) for text in source_text)))
            assert sha(diff.encode()) == record["provenance"]["diff_sha256"]
            expected, risk = record["expected"], record["expected"]["risk"]
            for revision in ("before", "after"):
                assert total(risk["components_" + revision]) == risk["risk_" + revision]
                assert category(risk["risk_" + revision]) == risk["category_" + revision]
            assert risk["risk_delta"] == risk["risk_after"] - risk["risk_before"]
            insufficient = (
                record["quality"]["incomplete"]
                or record["quality"]["confidence"] != "HIGH"
                or record["quality"]["coverage_ratio"] < 1
            )
            decision = (
                "BLOCK"
                if expected["added_paths"] and risk["risk_after"] >= 61
                else "REVIEW"
                if insufficient or risk["risk_after"] >= 41 or risk["risk_delta"] >= 21
                else "ALLOW"
            )
            assert expected["preliminary_verdict"] == expected["final_verdict"] == decision
            records.append(record)
    assert len(records) == manifest["count"]
    assert sum(r["safe_negative"] for r in records) / len(records) >= 0.4
    return records


def binary_metrics(expected, predicted):
    pairs = list(zip(expected, predicted, strict=True))
    tp = sum(a and b for a, b in pairs)
    fp = sum(not a and b for a, b in pairs)
    tn = sum(not a and not b for a, b in pairs)
    fn = sum(a and not b for a, b in pairs)

    def ratio(numerator, denominator):
        return numerator / denominator if denominator else 0.0

    precision, recall = ratio(tp, tp + fp), ratio(tp, tp + fn)
    return dict(
        TP=tp,
        FP=fp,
        TN=tn,
        FN=fn,
        precision=precision,
        recall=recall,
        F1=ratio(2 * precision * recall, precision + recall),
        false_positive_rate=ratio(fp, fp + tn),
        false_negative_rate=ratio(fn, fn + tp),
    )


def extraction_metrics(expected_rows, actual_rows):
    """Used when actual Spoon evidence is supplied. Missing endpoints count wrong."""
    expected = {(r["method"], r["endpoint"]): r for r in expected_rows}
    actual = {(r["method"], r["endpoint"]): r for r in actual_rows}
    keys = set(expected) | set(actual)
    denominator = len(keys) or 1
    common = set(expected) & set(actual)
    return dict(
        endpoint_extraction_accuracy=len(common) / denominator,
        authorization_extraction_accuracy=sum(
            (expected[k]["authentication"], expected[k]["required_role"])
            == (actual[k]["authentication"], actual[k]["required_role"])
            for k in common
        )
        / denominator,
        sensitivity_accuracy=sum(
            expected[k]["sensitivity"] == actual[k]["sensitivity"] for k in common
        )
        / denominator,
    )


def evaluate(records):
    from app.main import analyze
    from app.models import AnalysisRequest

    timings, gold, predictions, verdicts, paths, coverage = [], [], [], [], [], []
    for record in records:
        expected = record["expected"]
        start = time.perf_counter()
        result = analyze(
            AnalysisRequest(
                before=expected["before_ir"], after=expected["after_ir"], quality=record["quality"]
            )
        )
        timings.append((time.perf_counter() - start) * 1000)
        gold.append(bool(expected["added_paths"]))
        predictions.append(bool(result.graph_delta.new_paths))
        verdicts.append(expected["preliminary_verdict"] == result.verdict)
        paths.append(
            expected["added_paths"] == [p.nodes for p in result.graph_delta.new_paths]
            and expected["removed_paths"] == [p.nodes for p in result.graph_delta.removed_paths]
        )
        coverage.append(result.quality.coverage_ratio)
        assert (
            result.risk_result.risk_before,
            result.risk_result.risk_after,
            result.risk_result.risk_delta,
        ) == tuple(expected["risk"][k] for k in ("risk_before", "risk_after", "risk_delta"))
    return dict(
        **binary_metrics(gold, predictions),
        count=len(records),
        label_status="PROVISIONAL",
        scope="Synthetic canonical IR to graph/risk; not real-world vulnerability accuracy",
        endpoint_extraction_accuracy=None,
        authorization_extraction_accuracy=None,
        sensitivity_accuracy=None,
        extraction_metrics_reason="Spoon not executed by this IR-only evaluator; supply actual source results to extraction_metrics",
        graph_path_exact_match=statistics.mean(paths),
        verdict_accuracy=statistics.mean(verdicts),
        extraction_coverage=statistics.mean(coverage),
        coverage_source="supplied fixture quality, not measured extraction",
        average_runtime_ms=statistics.mean(timings),
        p95_runtime_ms=sorted(timings)[math.ceil(0.95 * len(timings)) - 1],
    )


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["generate", "validate", "evaluate"])
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.command == "generate":
        print(f"Generated {len(generate())} provisional records")
    else:
        records = validate()
        report = (
            {
                split: evaluate([r for r in records if r["split"] == split])
                for split in ("development", "calibration", "test")
            }
            if args.command == "evaluate"
            else dict(validated=len(records))
        )
        text = json.dumps(report, indent=2) + "\n"
        if args.output:
            args.output.write_text(text, encoding="utf-8", newline="\n")
        print(text)


if __name__ == "__main__":
    main()
