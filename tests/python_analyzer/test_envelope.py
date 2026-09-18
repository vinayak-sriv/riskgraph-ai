import json
from pathlib import Path

from jsonschema import Draft202012Validator
from python_analyzer_app.envelope import build_stub_envelope
from referencing import Registry, Resource

ROOT = Path(__file__).resolve().parents[2]


def _schema_registry(schema_dir: Path) -> Registry:
    registry = Registry()
    for dependency in sorted(schema_dir.glob("*.schema.json")):
        schema = json.loads(dependency.read_text(encoding="utf-8"))
        if "$id" in schema:
            registry = registry.with_resource(schema["$id"], Resource.from_contents(schema))
    return registry


def test_stub_envelope_is_schema_valid_for_a_non_git_directory(tmp_path):
    envelope = build_stub_envelope(str(tmp_path), "a" * 40, "b" * 40)

    schema_path = ROOT / "contracts" / "ir" / "analysis-envelope.schema.json"
    schema = json.loads(schema_path.read_text(encoding="utf-8"))
    registry = _schema_registry(schema_path.parent)
    errors = list(Draft202012Validator(schema, registry=registry).iter_errors(envelope))
    assert errors == []

    assert envelope["before"] == []
    assert envelope["after"] == []
    assert envelope["changed_files"] == []
    codes = {diagnostic["code"] for diagnostic in envelope["diagnostics"]}
    assert "PYTHON_EXTRACTION_NOT_IMPLEMENTED" in codes
    assert "GIT_DIFF_UNAVAILABLE" in codes  # tmp_path is not a git repository


def test_analysis_id_is_deterministic_and_commit_case_insensitive(tmp_path):
    first = build_stub_envelope(str(tmp_path), "a" * 40, "b" * 40)
    second = build_stub_envelope(str(tmp_path), "A" * 40, "B" * 40)

    assert first["analysis_id"] == second["analysis_id"]
    assert len(first["analysis_id"]) == 64
