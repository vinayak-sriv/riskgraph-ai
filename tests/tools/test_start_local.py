import importlib.util
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("start_local", ROOT / "tools/dev/start_local.py")
START_LOCAL = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(START_LOCAL)


def test_root_env_loader_does_not_override_explicit_local_security_settings(
    tmp_path, monkeypatch
) -> None:
    env_file = tmp_path / ".env"
    env_file.write_text("RISKGRAPH_SECURE_COOKIE=true\nRISKGRAPH_SERVICE_TOKEN=from-file\n")
    monkeypatch.setattr(START_LOCAL, "ROOT", tmp_path)
    environment = {"RISKGRAPH_SECURE_COOKIE": "false"}

    START_LOCAL.load_root_env(environment)

    assert environment["RISKGRAPH_SECURE_COOKIE"] == "false"
    assert environment["RISKGRAPH_SERVICE_TOKEN"] == "from-file"
