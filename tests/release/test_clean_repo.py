import importlib.util
import os
import stat
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("clean_repo", ROOT / "tools/dev/clean_repo.py")
CLEAN = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CLEAN)


def test_cleanup_removes_generated_artifacts_and_preserves_source(tmp_path):
    keep = tmp_path / "services" / "api" / "src" / "main.py"
    generated = [
        tmp_path / "apps" / "dashboard" / "node_modules" / "pkg" / "index.js",
        tmp_path / "services" / "platform-api" / "target" / "app.jar",
        tmp_path / "samples" / "generated" / "demo" / "Application.java",
        tmp_path / "tmp" / "runtime.log",
        tmp_path / "tools" / "dev" / "__pycache__" / "helper.pyc",
        tmp_path / "apps" / "dashboard.zip",
        tmp_path / "riskgraph-dataset-package.zip",
    ]
    keep.parent.mkdir(parents=True, exist_ok=True)
    keep.write_text("print('keep')\n")
    for path in generated:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(b"x" * 16)
    os.chmod(generated[2], stat.S_IREAD)

    freed, paths = CLEAN.cleanup(tmp_path)

    assert freed == 16 * len(generated)
    assert paths
    assert keep.exists()
    assert all(not path.exists() for path in generated)
