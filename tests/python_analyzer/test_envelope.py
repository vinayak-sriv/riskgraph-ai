import json
import subprocess
from pathlib import Path

from jsonschema import Draft202012Validator
from python_analyzer_app.envelope import build_envelope
from referencing import Registry, Resource

ROOT = Path(__file__).resolve().parents[2]


def _schema_registry(schema_dir: Path) -> Registry:
    registry = Registry()
    for dependency in sorted(schema_dir.glob("*.schema.json")):
        schema = json.loads(dependency.read_text(encoding="utf-8"))
        if "$id" in schema:
            registry = registry.with_resource(schema["$id"], Resource.from_contents(schema))
    return registry


def _validate(envelope: dict) -> None:
    schema_path = ROOT / "contracts" / "ir" / "analysis-envelope.schema.json"
    schema = json.loads(schema_path.read_text(encoding="utf-8"))
    registry = _schema_registry(schema_path.parent)
    errors = list(Draft202012Validator(schema, registry=registry).iter_errors(envelope))
    assert errors == []


def _git(repo: Path, *args: str) -> None:
    subprocess.run(["git", "-C", str(repo), *args], check=True, capture_output=True, text=True)


def _init_repo_with_two_commits(repo: Path) -> tuple[str, str]:
    repo.mkdir()
    _git(repo, "init", "-q")
    _git(repo, "config", "user.email", "test@example.com")
    _git(repo, "config", "user.name", "Test")

    routes = repo / "main.py"
    routes.write_text(
        'from fastapi import FastAPI\n\napp = FastAPI()\n\n\n@app.get("/public")\ndef list_public():\n    return []\n'
    )
    _git(repo, "add", "main.py")
    _git(repo, "commit", "-q", "-m", "initial")
    old_commit = subprocess.run(
        ["git", "-C", str(repo), "rev-parse", "HEAD"], capture_output=True, text=True, check=True
    ).stdout.strip()

    routes.write_text(
        "from fastapi import Depends, FastAPI\n\napp = FastAPI()\n\n\n"
        "def get_current_user():\n    ...\n\n\n"
        '@app.get("/public")\ndef list_public():\n    return []\n\n\n'
        '@app.post("/accounts")\ndef create_account(user=Depends(get_current_user)):\n    return {}\n'
    )
    _git(repo, "add", "main.py")
    _git(repo, "commit", "-q", "-m", "add authenticated route")
    new_commit = subprocess.run(
        ["git", "-C", str(repo), "rev-parse", "HEAD"], capture_output=True, text=True, check=True
    ).stdout.strip()

    return old_commit, new_commit


def test_stub_envelope_is_schema_valid_for_a_non_git_directory(tmp_path):
    envelope = build_envelope(str(tmp_path), "a" * 40, "b" * 40)

    _validate(envelope)
    assert envelope["before"] == []
    assert envelope["after"] == []
    assert envelope["changed_files"] == []
    codes = {diagnostic["code"] for diagnostic in envelope["diagnostics"]}
    assert "GIT_DIFF_UNAVAILABLE" in codes  # tmp_path is not a git repository


def test_analysis_id_is_deterministic_and_commit_case_insensitive(tmp_path):
    first = build_envelope(str(tmp_path), "a" * 40, "b" * 40)
    second = build_envelope(str(tmp_path), "A" * 40, "B" * 40)

    assert first["analysis_id"] == second["analysis_id"]
    assert len(first["analysis_id"]) == 64


def test_real_repo_extracts_routes_and_new_auth_dependency(tmp_path):
    repo = tmp_path / "repo"
    old_commit, new_commit = _init_repo_with_two_commits(repo)

    envelope = build_envelope(str(repo), old_commit, new_commit)

    _validate(envelope)
    before_controllers = {e["endpoint"]["controller"] for e in envelope["before"]}
    after_controllers = {e["endpoint"]["controller"] for e in envelope["after"]}
    assert before_controllers == {"list_public"}
    assert after_controllers == {"list_public", "create_account"}

    after_by_controller = {e["endpoint"]["controller"]: e for e in envelope["after"]}
    assert after_by_controller["create_account"]["endpoint"]["authentication"] is True
    # No repository/service calls in the handler body, so resolution falls
    # back to the handler's own name (see resolver.py's fallback_resource).
    assert after_by_controller["create_account"]["endpoint"]["resource"] == "create_account"

    codes = {diagnostic["code"] for diagnostic in envelope["diagnostics"]}
    assert "PYTHON_RESOLUTION_IS_HEURISTIC" in codes
