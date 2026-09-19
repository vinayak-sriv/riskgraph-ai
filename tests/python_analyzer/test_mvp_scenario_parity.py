"""Track A batch 5: parity across the four MVP scenarios (authorization
removal, safe change, new public sensitive endpoint, sensitive resource
exposure), mirroring the Java analyzer's DemoScenarioService scenarios so
the Python/FastAPI analyzer produces the same shape of evidence for each.
"""

import json
from pathlib import Path

from jsonschema import Draft202012Validator
from python_analyzer_app.envelope import build_envelope
from referencing import Registry, Resource

from .conftest import build_two_commit_repo

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


def _endpoint(envelope: dict, side: str, controller: str) -> dict:
    by_controller = {e["endpoint"]["controller"]: e["endpoint"] for e in envelope[side]}
    return by_controller[controller]


def test_authorization_removal(tmp_path):
    repo = tmp_path / "repo"
    before = (
        "from fastapi import Depends, FastAPI\n\n"
        "app = FastAPI()\n\n\n"
        "def require_admin():\n    ...\n\n\n"
        '@app.get("/admin/export")\n'
        "def export_customers(user=Depends(require_admin)):\n"
        "    return customer_repository.list_all()\n"
    )
    after = (
        "from fastapi import FastAPI\n\n"
        "app = FastAPI()\n\n\n"
        '@app.get("/admin/export")\n'
        "def export_customers():\n"
        "    return customer_repository.list_all()\n"
    )
    old_commit, new_commit = build_two_commit_repo(repo, before, after)

    envelope = build_envelope(str(repo), old_commit, new_commit)
    _validate(envelope)

    before_endpoint = _endpoint(envelope, "before", "export_customers")
    after_endpoint = _endpoint(envelope, "after", "export_customers")
    assert before_endpoint["authentication"] is True
    assert after_endpoint["authentication"] is False
    assert before_endpoint["resource"] == after_endpoint["resource"] == "customer"
    assert before_endpoint["sensitivity"] == after_endpoint["sensitivity"] == "HIGH"


def test_safe_change(tmp_path):
    repo = tmp_path / "repo"
    before = (
        "from fastapi import FastAPI\n\n"
        "app = FastAPI()\n\n\n"
        '@app.get("/catalog/{item_id}")\n'
        "def get_item(item_id: str):\n"
        "    return catalog_repository.find_by_id(item_id)\n"
    )
    after = (
        "from fastapi import FastAPI\n\n"
        "app = FastAPI()\n\n\n"
        "def _log(message: str) -> None:\n"
        "    print(message)\n\n\n"
        '@app.get("/catalog/{item_id}")\n'
        "def get_item(item_id: str):\n"
        '    _log("fetching item")\n'
        "    return catalog_repository.find_by_id(item_id)\n"
    )
    old_commit, new_commit = build_two_commit_repo(repo, before, after)

    envelope = build_envelope(str(repo), old_commit, new_commit)
    _validate(envelope)

    # Only the code around the route changed; the route's own security
    # evidence -- everything under "endpoint" -- must come out identical.
    assert _endpoint(envelope, "before", "get_item") == _endpoint(envelope, "after", "get_item")


def test_new_public_sensitive_endpoint(tmp_path):
    repo = tmp_path / "repo"
    before = "from fastapi import FastAPI\n\napp = FastAPI()\n"
    after = (
        "from fastapi import FastAPI\n\n"
        "app = FastAPI()\n\n\n"
        '@app.get("/payments/{payment_id}")\n'
        "def get_payment(payment_id: str):\n"
        "    return payment_repository.find_by_id(payment_id)\n"
    )
    old_commit, new_commit = build_two_commit_repo(repo, before, after)

    envelope = build_envelope(str(repo), old_commit, new_commit)
    _validate(envelope)

    assert envelope["before"] == []
    after_endpoint = _endpoint(envelope, "after", "get_payment")
    assert after_endpoint["authentication"] is False
    assert after_endpoint["resource"] == "payment"
    assert after_endpoint["sensitivity"] == "CRITICAL"


def test_sensitive_resource_exposure(tmp_path):
    repo = tmp_path / "repo"
    before = (
        "from fastapi import FastAPI\n\n"
        "app = FastAPI()\n\n\n"
        '@app.get("/items/{item_id}")\n'
        "def get_item(item_id: str):\n"
        "    return catalog_repository.find_by_id(item_id)\n"
    )
    after = (
        "from fastapi import FastAPI\n\n"
        "app = FastAPI()\n\n\n"
        '@app.get("/items/{item_id}")\n'
        "def get_item(item_id: str):\n"
        "    return user_repository.find_by_id(item_id)\n"
    )
    old_commit, new_commit = build_two_commit_repo(repo, before, after)

    envelope = build_envelope(str(repo), old_commit, new_commit)
    _validate(envelope)

    before_endpoint = _endpoint(envelope, "before", "get_item")
    after_endpoint = _endpoint(envelope, "after", "get_item")
    # Same route, same shape -- only the backing resource's sensitivity
    # changed, which is exactly what this scenario is meant to catch.
    assert before_endpoint["resource"] == "catalog"
    assert before_endpoint["sensitivity"] == "MEDIUM"
    assert after_endpoint["resource"] == "user"
    assert after_endpoint["sensitivity"] == "HIGH"
