import importlib.util
import subprocess
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
    assert "expected_probe_id=" in script
    assert 'test "$${actual_probe_id}" = "$${expected_probe_id}"' in script
    assert "docker image inspect riskgraph-validation-probe:local" in script
    assert script.index('docker pull "$${RISKGRAPH_VALIDATION_PROBE_IMAGE}"') < script.index("fi\n")

    workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
    assert "riskgraph-validation-probe:local" not in workflow
    assert "docker save --output" not in workflow


def test_validation_archive_is_atomic_and_contains_every_runtime_image(tmp_path, monkeypatch):
    monkeypatch.setattr(BUILDER, "ROOT", tmp_path)
    commands = []

    def run(command, check, **kwargs):
        assert check is True
        commands.append(command)
        if command[1:3] == ["image", "inspect"]:
            assert kwargs == {"stdout": subprocess.DEVNULL}
        if command[1:3] == ["save", "--output"]:
            assert kwargs == {}
            Path(command[3]).write_bytes(b"archive")
        return subprocess.CompletedProcess(command, 0)

    monkeypatch.setattr(BUILDER.subprocess, "run", run)
    monkeypatch.setattr(
        BUILDER.subprocess,
        "check_output",
        lambda command, text: "sha256:" + "a" * 64 + "\n",
    )
    output = BUILDER.archive_validation_images(["docker"], BUILDER.DEFAULT_PROBE_IMAGE)

    assert output.read_bytes() == b"archive"
    assert (
        output.with_name("probe-image-id").read_text(encoding="ascii")
        == "sha256:" + "a" * 64 + "\n"
    )
    assert not output.with_suffix(".tar.part").exists()
    assert commands[-1] == [
        "docker",
        "save",
        "--output",
        str(output.with_suffix(".tar.part")),
        *BUILDER.SANDBOX_IMAGES,
        BUILDER.LOCAL_PROBE_IMAGE,
    ]


def test_validation_archive_rejects_a_non_sha256_probe_identity(tmp_path, monkeypatch):
    monkeypatch.setattr(BUILDER, "ROOT", tmp_path)
    monkeypatch.setattr(
        BUILDER.subprocess,
        "run",
        lambda command, check, **kwargs: subprocess.CompletedProcess(command, 0),
    )
    monkeypatch.setattr(
        BUILDER.subprocess,
        "check_output",
        lambda command, text: "sha256:" + "z" * 64 + "\n",
    )

    with pytest.raises(ValueError, match="immutable SHA-256"):
        BUILDER.archive_validation_images(["docker"], BUILDER.DEFAULT_PROBE_IMAGE)
