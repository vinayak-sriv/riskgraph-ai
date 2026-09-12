import copy
import importlib.util
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("reporting", ROOT / "tools/reporting/generate.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def scan():
    import json

    return json.loads((ROOT / "apps/dashboard/src/fixtures.json").read_text())[
        "authorization-removal"
    ]


def test_fingerprints_ignore_commit_and_line_churn():
    one = scan()
    one["provenance"] = dict(repository_identity="local-demo", new_commit="a" * 40)
    two = copy.deepcopy(one)
    two["provenance"]["new_commit"] = "b" * 40
    a = module.reports(one)["results.sarif"]["runs"][0]["results"]
    b = module.reports(two)["results.sarif"]["runs"][0]["results"]
    assert a == b
    assert len(a[0]["partialFingerprints"]["riskgraph/v1"]) == 64


def test_safe_change_is_success_without_findings():
    value = scan()
    value["graph_delta"]["new_paths"] = []
    value["verdict"] = value["final_verdict"] = "ALLOW"
    report = module.reports(value)
    assert report["check.json"]["conclusion"] == "success"
    assert report["results.sarif"]["runs"][0]["results"] == []


def test_annotations_have_real_source_and_reject_traversal():
    value = scan()
    row = dict(
        endpoint=dict(method="GET", endpoint="/admin/export"),
        source_location=dict(path="src/Admin.java", start_line=7, end_line=9),
    )
    value["source_evidence"] = dict(after=[row])
    assert module.reports(value)["check.json"]["output"]["annotations"][0]["start_line"] == 7
    row["source_location"]["path"] = "../secrets"
    assert module.reports(value)["check.json"]["output"]["annotations"] == []


def test_sarif_validates_against_official_oasis_schema():
    import json

    from jsonschema.validators import validator_for

    schema = json.loads((ROOT / "contracts/reporting/sarif-2.1.0.schema.json").read_text())
    validator = validator_for(schema)(schema)
    validator.validate(module.reports(scan())["results.sarif"])
    value = scan()
    value["graph_delta"]["new_paths"] = []
    validator.validate(module.reports(value)["results.sarif"])
