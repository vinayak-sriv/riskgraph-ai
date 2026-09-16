import importlib.util
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "release_fixture_generator", ROOT / "tools/dev/create_mvp_samples.py"
)
FIXTURES = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(FIXTURES)


def test_pull_request_example_tracks_current_authorization_removal_fixture(tmp_path, monkeypatch):
    event = json.loads(
        (ROOT / "contracts/github/examples/pull-request.json").read_text(encoding="utf-8")
    )
    monkeypatch.setattr(FIXTURES, "OUTPUT", tmp_path / "mvp-v2")
    manifest = FIXTURES.create()
    scenario = manifest["scenarios"]["authorization-removal"]

    assert event["pull_request"]["base"]["sha"] == scenario["old_commit"]
    assert event["pull_request"]["head"]["sha"] == scenario["new_commit"]
