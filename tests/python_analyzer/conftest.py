import importlib.util
import subprocess
import sys
from pathlib import Path

import pytest

path = Path(__file__).resolve().parents[2] / "services/python-analyzer/app"
spec = importlib.util.spec_from_file_location(
    "python_analyzer_app", path / "__init__.py", submodule_search_locations=[str(path)]
)
module = importlib.util.module_from_spec(spec)
sys.modules["python_analyzer_app"] = module
spec.loader.exec_module(module)


def _git(repo: Path, *args: str) -> None:
    subprocess.run(["git", "-C", str(repo), *args], check=True, capture_output=True, text=True)


def build_two_commit_repo(
    repo: Path, before_source: str, after_source: str, filename: str = "main.py"
) -> tuple[str, str]:
    """Writes `before_source`/`after_source` as two commits to a fresh git repo
    at `repo` and returns (old_commit, new_commit), for tests that exercise
    `build_envelope` end to end against a real repository."""
    repo.mkdir()
    _git(repo, "init", "-q")
    _git(repo, "config", "user.email", "test@example.com")
    _git(repo, "config", "user.name", "Test")

    target = repo / filename
    target.write_text(before_source)
    _git(repo, "add", filename)
    _git(repo, "commit", "-q", "-m", "before")
    old_commit = subprocess.run(
        ["git", "-C", str(repo), "rev-parse", "HEAD"], capture_output=True, text=True, check=True
    ).stdout.strip()

    target.write_text(after_source)
    _git(repo, "add", filename)
    _git(repo, "commit", "-q", "-m", "after")
    new_commit = subprocess.run(
        ["git", "-C", str(repo), "rev-parse", "HEAD"], capture_output=True, text=True, check=True
    ).stdout.strip()

    return old_commit, new_commit


@pytest.fixture(autouse=True)
def allow_tmp_repository_roots(tmp_path, monkeypatch):
    """The analyzer rejects any repository outside RISKGRAPH_ALLOWED_REPOSITORY_ROOTS.

    Tests build throwaway repositories under tmp_path, so allow that root.
    """
    monkeypatch.setenv("RISKGRAPH_ALLOWED_REPOSITORY_ROOTS", str(tmp_path.resolve()))
