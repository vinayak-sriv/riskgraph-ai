import importlib.util
import sys
from pathlib import Path

import pytest
import yaml

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/dev"))
SPEC = importlib.util.spec_from_file_location(
    "build_sandboxes", ROOT / "tools/dev/build_sandboxes.py"
)
BUILDER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BUILDER)


def test_native_builder_rejects_mutable_probe_image():
    assert BUILDER.approved_probe_image(BUILDER.DEFAULT_PROBE_IMAGE) == BUILDER.DEFAULT_PROBE_IMAGE
    with pytest.raises(SystemExit, match="not an approved digest-pinned image"):
        BUILDER.approved_probe_image("python:latest")


def test_compose_loader_quotes_and_allowlists_environment_probe_image():
    compose = yaml.safe_load(
        (ROOT / "infrastructure/docker-compose.validation.yml").read_text(encoding="utf-8")
    )
    loader = compose["services"]["validation-image-loader"]
    script = loader["command"][2]

    assert 'case "$${RISKGRAPH_VALIDATION_PROBE_IMAGE}"' in script
    assert 'docker pull "$${RISKGRAPH_VALIDATION_PROBE_IMAGE}"' in script
    assert 'docker image inspect "$${RISKGRAPH_VALIDATION_PROBE_IMAGE}"' in script
