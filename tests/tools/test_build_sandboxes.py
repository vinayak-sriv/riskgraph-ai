import hashlib
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
    assert "expected_archive_sha=" in script
    assert "sha256sum /workspace/preloaded/images.tar" in script
    assert 'test "$${actual_archive_sha}" = "$${expected_archive_sha}"' in script
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
    output = BUILDER.archive_validation_images(["docker"], BUILDER.DEFAULT_PROBE_IMAGE)

    assert output.read_bytes() == b"archive"
    assert output.with_name("images.sha256").read_text(encoding="ascii") == (
        hashlib.sha256(b"archive").hexdigest() + "\n"
    )
    assert not output.with_suffix(".tar.part").exists()
    assert not output.with_name("images.part").exists()
    assert commands[-1] == [
        "docker",
        "save",
        "--output",
        str(output.with_suffix(".tar.part")),
        *BUILDER.SANDBOX_IMAGES,
        BUILDER.LOCAL_PROBE_IMAGE,
    ]


def test_python_sandbox_pair_is_registered_alongside_the_java_sandboxes():
    assert "riskgraph-sandbox-protected-py:local" in BUILDER.SANDBOX_IMAGES
    assert "riskgraph-sandbox-vulnerable-py:local" in BUILDER.SANDBOX_IMAGES

    compose = yaml.safe_load(
        (ROOT / "infrastructure/docker-compose.validation.yml").read_text(encoding="utf-8")
    )
    script = compose["services"]["validation-image-loader"]["command"][2]
    assert "riskgraph-sandbox-protected-py:local" in script
    assert "riskgraph-sandbox-vulnerable-py:local" in script


def test_python_sandbox_source_matches_the_trusted_fixture(tmp_path, monkeypatch):
    import create_mvp_samples_python as py_fixture

    monkeypatch.setattr(py_fixture, "OUTPUT", tmp_path / "mvp-v2-python")
    pair = py_fixture.create()["scenarios"]["authorization-removal"]

    for commit, expected_handler in [
        (pair["old_commit"], py_fixture.HANDLER_PROTECTED),
        (pair["new_commit"], py_fixture.HANDLER_VULNERABLE),
    ]:
        actual = BUILDER.git(Path(pair["repository_path"]), "show", f"{commit}:app/main.py") + "\n"
        assert actual == expected_handler


def test_file_sha256_streams_the_complete_file(tmp_path):
    payload = b"a" * (1024 * 1024 + 3)
    source = tmp_path / "large.bin"
    source.write_bytes(payload)

    assert BUILDER.file_sha256(source) == hashlib.sha256(payload).hexdigest()
