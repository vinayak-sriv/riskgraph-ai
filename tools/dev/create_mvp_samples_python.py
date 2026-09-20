"""Create an isolated, deterministic synthetic Git fixture for the Python/FastAPI
runtime-validation sandbox: the "authorization-removal" scenario only, since that
is the one scenario with an HTTP-observable before/after difference. Mirrors
create_mvp_samples.py's Java scenario (same route, same shared "list_all"
resource call); reuses its git/commit_fixture helpers. Never touches project
Git history.
"""

import json
from pathlib import Path

from create_mvp_samples import commit_fixture, git

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "samples/generated/mvp-v2-python"
SOURCE = "app/"
# No __init__.py: uvicorn imports "app.main:app" as a namespace package (Python
# 3.3+), so the relative "from . import customer_repository" in the handlers
# below works without one, and every FILES entry needs real trailing-newline
# content for the git-blob comparison in build_sandboxes.py to line up.
FILES = {
    SOURCE + "customer_repository.py": (
        'def list_all() -> dict:\n    return {"customer_export": "sandbox-only"}\n'
    ),
}
HANDLER_PROTECTED = """from fastapi import Depends, FastAPI, HTTPException

from . import customer_repository

app = FastAPI()


def require_admin():
    # No admin session is ever configured in this sandbox: every anonymous
    # probe must be denied, proving the endpoint is unreachable without it.
    raise HTTPException(status_code=403, detail="Admin authentication required")


@app.get("/admin/export")
def export_customers(user=Depends(require_admin)):
    return customer_repository.list_all()
"""
HANDLER_VULNERABLE = """from fastapi import FastAPI

from . import customer_repository

app = FastAPI()


@app.get("/admin/export")
def export_customers():
    return customer_repository.list_all()
"""


def create():
    manifest_path = OUTPUT / "manifest.json"
    if OUTPUT.exists():
        if not manifest_path.exists():
            raise SystemExit("Refusing to overwrite existing fixture directory")
        return json.loads(manifest_path.read_text())
    OUTPUT.mkdir(parents=True)
    repo = OUTPUT / "authorization-removal"
    repo.mkdir()
    git(repo, "init", "--quiet")
    old = commit_fixture(repo, {**FILES, SOURCE + "main.py": HANDLER_PROTECTED})
    new = commit_fixture(repo, {**FILES, SOURCE + "main.py": HANDLER_VULNERABLE}, old)
    result = {
        "version": "1.0.0",
        "source_type": "SYNTHETIC_AI_ASSISTED",
        "scenarios": {
            "authorization-removal": dict(
                repository_path=str(repo.resolve()), old_commit=old, new_commit=new
            )
        },
    }
    manifest_path.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8", newline="\n")
    return result


if __name__ == "__main__":
    print(json.dumps(create(), indent=2))
