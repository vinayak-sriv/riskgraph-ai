"""Verify running services, four source scenarios, contracts and repeatability."""

import argparse
import json
import time

from create_mvp_samples import ROOT, create
from jsonschema import Draft202012Validator
from platform_session import PlatformSession
from referencing import Registry, Resource

SESSION = PlatformSession()


def call(route, payload=None):
    return SESSION.call(route, payload)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--validation", action="store_true")
    parser.add_argument(
        "--require-ai", action="store_true", help="Require live Ollama on each source finding"
    )
    parser.add_argument("--compose", action="store_true")
    args = parser.parse_args()
    output = ROOT / "tmp/evidence-bundle"
    output.mkdir(parents=True, exist_ok=True)
    registry = Registry()
    for path in (ROOT / "contracts").rglob("*.schema.json"):
        schema = json.loads(path.read_text())
        resource = Resource.from_contents(schema)
        registry = registry.with_resource(path.resolve().as_uri(), resource)
        registry = registry.with_resource(
            "https://riskgraph.ai/contracts/" + path.relative_to(ROOT / "contracts").as_posix(),
            resource,
        )
    schema = json.loads((ROOT / "contracts/ir/scan-result.schema.json").read_text())
    expected = {
        "authorization-removal": (22, 91, "BLOCK"),
        "safe-change": (22, 22, "ALLOW"),
        "new-public-sensitive-endpoint": (0, 65, "BLOCK"),
        "sensitive-resource-exposure": (8, 65, "BLOCK"),
    }
    summary = {}
    manifest = create()
    if args.compose:
        manifest = json.loads((ROOT / "samples/generated/mvp-v2/manifest.compose.json").read_text())
    extraction_count = 0
    protected = dict(
        endpoint="/admin/export",
        method="GET",
        controller="ExportController",
        authentication=True,
        required_role="ADMIN",
        service="ExportService",
        repository="PaymentRepository",
        resource="Payment",
        sensitivity="CRITICAL",
    )
    public = {**protected, "authentication": False, "required_role": None}
    catalog = {
        **public,
        "service": "CatalogService",
        "repository": "CatalogRepository",
        "resource": "Catalog",
        "sensitivity": "MODERATE",
    }
    expected_ir = {
        "authorization-removal": ([protected], [public]),
        "safe-change": ([protected], [protected]),
        "new-public-sensitive-endpoint": ([], [public]),
        "sensitive-resource-exposure": ([catalog], [public]),
    }
    for name, request in manifest["scenarios"].items():
        started = time.perf_counter()
        result = call("/analyses", request)
        runtime = time.perf_counter() - started
        Draft202012Validator(schema, registry=registry).validate(result)
        repeat = call("/analyses", request)
        for key in (
            "scan_id",
            "graph_delta",
            "risk_result",
            "provenance",
            "source_evidence",
            "quality",
            "reason_codes",
        ):
            assert result[key] == repeat[key], (name, "nondeterministic", key)
        actual = (
            result["risk_result"]["risk_before"],
            result["risk_result"]["risk_after"],
            result["final_verdict"],
        )
        assert actual == expected[name], (name, actual, expected[name])
        assert len(result["graph_delta"]["new_paths"]) == (0 if name == "safe-change" else 1)
        if args.require_ai and name != "safe-change":
            assert result["ai"]["status"] == "AVAILABLE", (name, result["ai"])
            assert (
                result["ai"]["analysis"]["recommended_test"]
                == "GET /admin/export without authentication"
            )
        assert result["quality"] == dict(confidence="HIGH", coverage_ratio=1.0, incomplete=False)
        for revision, gold in zip(("before", "after"), expected_ir[name], strict=False):
            actual_ir = [row["endpoint"] for row in result["source_evidence"][revision]]
            assert actual_ir == gold, (
                name,
                revision,
                "source extraction mismatch",
                actual_ir,
                gold,
            )
            extraction_count += len(gold)
        (output / f"{name}.json").write_text(
            json.dumps(result, indent=2) + "\n", encoding="utf-8", newline="\n"
        )
        summary[name] = dict(
            risk_before=actual[0],
            risk_after=actual[1],
            verdict=actual[2],
            new_paths=len(result["graph_delta"]["new_paths"]),
            runtime_seconds=runtime,
            source_locations=sum(len(v) for v in result["source_evidence"].values()),
            confidence=result["quality"]["confidence"],
            ai_status=result.get("ai", {}).get("status", "NOT_RUN"),
        )
        if name == "authorization-removal" and args.validation:
            validated = call(f"/analyses/{result['scan_id']}/validation", {})
            assert validated["validation_status"] == "CONFIRMED", validated.get("validation")
            Draft202012Validator(schema, registry=registry).validate(validated)
            summary[name]["validation_status"] = "CONFIRMED"
            reanalyzed = call("/analyses", request)
            assert reanalyzed["validation_status"] == "CONFIRMED", (
                "Repeat analysis discarded validation"
            )
            assert reanalyzed["validation"] == validated["validation"], (
                "Repeat analysis changed runtime evidence"
            )
            (output / "validated.json").write_text(
                json.dumps(validated, indent=2) + "\n", encoding="utf-8", newline="\n"
            )
    metrics = dict(
        scope="Four authored real Git pairs; seven source-located IR rows, not external accuracy",
        expected_rows=extraction_count,
        endpoint_extraction_accuracy=1.0,
        authorization_extraction_accuracy=1.0,
        sensitivity_accuracy=1.0,
        canonical_ir_exact_match=1.0,
        extraction_coverage=1.0,
    )
    (output / "source-extraction-metrics.json").write_text(
        json.dumps(metrics, indent=2) + "\n", encoding="utf-8", newline="\n"
    )
    (output / "summary.json").write_text(
        json.dumps(summary, indent=2) + "\n", encoding="utf-8", newline="\n"
    )
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
