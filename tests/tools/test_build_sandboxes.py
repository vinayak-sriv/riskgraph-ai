import hashlib
import importlib.util
import io
import json
import subprocess
import sys
import tarfile
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


def write_docker_archive(path: Path, config: bytes = b'{"architecture":"amd64"}') -> str:
    digest = hashlib.sha256(config).hexdigest()
    config_path = "blobs/sha256/" + digest
    manifest = json.dumps(
        [
            {
                "Config": config_path,
                "RepoTags": [BUILDER.LOCAL_PROBE_IMAGE],
                "Layers": [],
            }
        ]
    ).encode()
    with tarfile.open(path, "w") as bundle:
        for name, content in (("manifest.json", manifest), (config_path, config)):
            info = tarfile.TarInfo(name)
            info.size = len(content)
            bundle.addfile(info, io.BytesIO(content))
    return "sha256:" + digest


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
    expected_image_id = ""

    def run(command, check, **kwargs):
        assert check is True
        commands.append(command)
        if command[1:3] == ["image", "inspect"]:
            assert kwargs == {"stdout": subprocess.DEVNULL}
        if command[1:3] == ["save", "--output"]:
            assert kwargs == {}
            nonlocal expected_image_id
            expected_image_id = write_docker_archive(Path(command[3]))
        return subprocess.CompletedProcess(command, 0)

    monkeypatch.setattr(BUILDER.subprocess, "run", run)
    output = BUILDER.archive_validation_images(["docker"], BUILDER.DEFAULT_PROBE_IMAGE)

    assert output.exists()
    assert (
        output.with_name("probe-image-id").read_text(encoding="ascii") == expected_image_id + "\n"
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

    def run(command, check, **kwargs):
        if command[1:3] == ["save", "--output"]:
            path = Path(command[3])
            manifest = json.dumps(
                [
                    {
                        "Config": "blobs/sha256/" + "z" * 64,
                        "RepoTags": [BUILDER.LOCAL_PROBE_IMAGE],
                        "Layers": [],
                    }
                ]
            ).encode()
            with tarfile.open(path, "w") as bundle:
                info = tarfile.TarInfo("manifest.json")
                info.size = len(manifest)
                bundle.addfile(info, io.BytesIO(manifest))
        return subprocess.CompletedProcess(command, 0)

    monkeypatch.setattr(BUILDER.subprocess, "run", run)

    with pytest.raises(ValueError, match="config path"):
        BUILDER.archive_validation_images(["docker"], BUILDER.DEFAULT_PROBE_IMAGE)


def test_archive_identity_rejects_a_modified_config_blob(tmp_path):
    archive = tmp_path / "images.tar"
    image_id = write_docker_archive(archive)
    with tarfile.open(archive, "a") as bundle:
        config_path = "blobs/sha256/" + image_id.removeprefix("sha256:")
        modified = b'{"architecture":"arm64"}'
        info = tarfile.TarInfo(config_path)
        info.size = len(modified)
        bundle.addfile(info, io.BytesIO(modified))

    with pytest.raises(ValueError, match="failed its SHA-256 check"):
        BUILDER.archived_image_id(archive, BUILDER.LOCAL_PROBE_IMAGE)
