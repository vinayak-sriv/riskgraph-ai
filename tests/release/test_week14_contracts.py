import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def test_demo_recording_is_reproducible_and_never_targets_a_live_system():
    package = json.loads((ROOT / "apps/dashboard/package.json").read_text(encoding="utf-8"))
    config = (ROOT / "apps/dashboard/playwright.demo.config.ts").read_text(encoding="utf-8")
    spec = (ROOT / "apps/dashboard/e2e/demo-recording.demo.ts").read_text(encoding="utf-8")
    collector = (ROOT / "tools/demo/collect-recording.mjs").read_text(encoding="utf-8")

    assert package["scripts"]["demo:record"].endswith("tools/demo/collect-recording.mjs")
    assert 'video: { mode: "on"' in config
    assert 'page.route("http://localhost:8080/**"' in spec
    assert "https://" not in spec
    assert "authorization-removal" in spec
    assert "safe-change" in spec
    assert "new-public-sensitive-endpoint" in spec
    assert 'artifact: "riskgraph-demo.webm"' in collector
    assert 'createHash("sha256")' in collector
    assert 'execFileSync("git", ["rev-parse", "HEAD"]' in collector


def test_ci_retains_a_bounded_demo_artifact_and_local_gate_records_it():
    workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
    verifier = (ROOT / "tools/dev/verify_release.py").read_text(encoding="utf-8")

    assert "npm run demo:record" in workflow
    assert "riskgraph-demo-recording" in workflow
    assert "retention-days: 14" in workflow
    assert '"demo-recording", ["npm", "run", "demo:record"]' in verifier
    assert '"--cov-fail-under=93"' in verifier


def test_week14_public_deliverables_exist_and_keep_the_human_gate_explicit():
    # Dated reports and weekly progress notes now live under docs/archive/.
    required = [
        ROOT / "docs/archive/report-draft.md",
        ROOT / "docs/demo-recording.md",
        ROOT / "docs/archive/week-14-progress.md",
    ]
    for path in required:
        assert path.is_file(), path

    week = (ROOT / "docs/archive/week-14-progress.md").read_text(encoding="utf-8")
    plan = (ROOT / "docs/week-plan.md").read_text(encoding="utf-8")
    assert "independent human review" in week.lower()
    assert "gate-limited" in plan.lower()


def test_public_guidance_stays_in_one_root_readme():
    tracked = subprocess.check_output(
        ["git", "ls-files"], cwd=ROOT, text=True, encoding="utf-8"
    ).splitlines()
    readmes = sorted(path for path in tracked if Path(path).name.lower().startswith("readme"))
    assert readmes == ["README.md"]

    content = (ROOT / "README.md").read_text(encoding="utf-8")
    for heading in (
        "# RiskGraph AI",
        "## Quick start",
        "## Services and responsibilities",
        "## Samples and evaluation data",
        "## Evidence bundles",
    ):
        assert heading in content
