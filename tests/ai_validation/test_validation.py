import json
import subprocess

import pytest
from ai_app.validation import (
    DEFAULT_PROBE_IMAGE,
    DockerRunner,
    ValidationRequest,
    ValidationResult,
    approved_probe_image,
)
from pydantic import ValidationError


@pytest.mark.parametrize(
    "fields",
    [
        dict(target_base_url="http://example.com"),
        dict(path="//example.com"),
        dict(path="/%2f%2fevil"),
        dict(headers={"Host": "evil"}),
        dict(body="shell"),
        dict(method="POST"),
        dict(sandbox_revision="http://127.0.0.1"),
    ],
)
def test_arbitrary_targets_and_requests_rejected(fields):
    with pytest.raises(ValidationError):
        ValidationRequest.model_validate({"sandbox_revision": "vulnerable", **fields})


class FakeDocker(DockerRunner):
    def __init__(self, outcome="CONFIRMED", timeout=False, cleanup_failure=False):
        self.calls = []
        self.outcome = outcome
        self.timeout = timeout
        self.cleanup_failure = cleanup_failure

    def run(self, args, timeout=20):
        self.calls.append(args)
        if args[:2] == ["image", "inspect"]:
            return json.dumps(
                [{"Id": "sha256:" + "a" * 64, "Config": {"Labels": {"ai.riskgraph.sandbox": "1"}}}]
            )
        if args[0] == "run" and "python" in args:
            if self.timeout:
                raise subprocess.TimeoutExpired(args, timeout)
            return json.dumps(
                {
                    "status": self.outcome,
                    "actual_status": 200 if self.outcome == "CONFIRMED" else 403,
                    "reason_code": "HTTP_AUTH_PROBE",
                }
            )
        if args[0] == "rm" and self.cleanup_failure:
            raise OSError("unavailable")
        return "id"


@pytest.mark.parametrize("outcome", ["CONFIRMED", "REJECTED", "INCONCLUSIVE"])
def test_isolation_status_and_cleanup(outcome):
    runner = FakeDocker(outcome)
    result = runner.validate(ValidationRequest(sandbox_revision="vulnerable"))
    assert result.status == outcome and result.cleanup_complete
    assert result.probe_image_id == "sha256:" + "a" * 64
    assert result.confirmed == (outcome == "CONFIRMED")
    for command in (c for c in runner.calls if c[0] == "run"):
        assert all(
            flag in command
            for flag in [
                "--read-only",
                "--cap-drop=ALL",
                "--user",
                "--cpus=.5",
                "--memory=256m",
                "--pids-limit=64",
            ]
        )
        assert "--publish" not in command and "host" not in command
    assert "--internal" in runner.calls[2]
    assert sum(c[0] == "rm" for c in runner.calls) == 2
    assert runner.calls[-1][:2] == ["network", "rm"]


def test_timeout_and_cleanup_failure_are_explicit():
    runner = FakeDocker(timeout=True)
    result = runner.validate(ValidationRequest(sandbox_revision="vulnerable"))
    assert result.status == "ERROR" and result.reason_code == "DOCKER_TIMEOUT"
    assert runner.calls[-1][:2] == ["network", "rm"]
    result = FakeDocker(cleanup_failure=True).validate(
        ValidationRequest(sandbox_revision="vulnerable")
    )
    assert result.status == "ERROR" and not result.confirmed and not result.cleanup_complete


def test_commit_binding_rejects_mismatched_image_before_network_creation():
    runner = FakeDocker()
    result = runner.validate(
        ValidationRequest(sandbox_revision="vulnerable", expected_commit="b" * 40)
    )
    assert result.status == "ERROR" and not result.confirmed
    assert runner.calls == [["image", "inspect", "riskgraph-sandbox-vulnerable:local"]]


def test_remote_docker_context_cannot_select_validation_target(monkeypatch):
    monkeypatch.setenv("DOCKER_HOST", "tcp://example.com:2375")
    monkeypatch.setenv("DOCKER_CONTEXT", "remote")
    command = DockerRunner().command
    assert command[:2] == ["docker", "--host"]
    assert command[2] in (
        "npipe:////./pipe/dockerDesktopLinuxEngine",
        "unix:///var/run/docker.sock",
    )


def test_only_dedicated_validation_daemon_can_be_configured(monkeypatch):
    monkeypatch.setenv("RISKGRAPH_VALIDATION_DOCKER_HOST", "tcp://attacker.example:2375")
    with pytest.raises(ValueError, match="not allowlisted"):
        DockerRunner()
    monkeypatch.setenv("RISKGRAPH_VALIDATION_DOCKER_HOST", "tcp://validation-docker:2375")
    assert DockerRunner().command[-1] == "tcp://validation-docker:2375"


def test_isolated_mode_fails_closed_without_daemon(monkeypatch):
    monkeypatch.delenv("RISKGRAPH_VALIDATION_DOCKER_HOST", raising=False)
    monkeypatch.setenv("RISKGRAPH_REQUIRE_ISOLATED_DOCKER", "true")
    with pytest.raises(ValueError, match="required"):
        DockerRunner()


def test_probe_image_must_match_the_digest_pinned_allowlist():
    assert approved_probe_image(DEFAULT_PROBE_IMAGE) == DEFAULT_PROBE_IMAGE
    with pytest.raises(ValueError, match="not an approved digest-pinned image"):
        approved_probe_image("python:latest")


@pytest.mark.parametrize(
    "payload",
    [
        {"status": "REJECTED", "confirmed": True, "actual_status": 403},
        {"status": "CONFIRMED", "confirmed": False, "actual_status": 200},
        {"status": "CONFIRMED", "confirmed": True, "actual_status": 403},
        {"status": "REJECTED", "confirmed": False, "actual_status": 200},
        {"status": "REJECTED", "confirmed": False, "actual_status": 403, "cleanup_complete": False},
    ],
)
def test_contradictory_validation_results_are_rejected(payload):
    with pytest.raises(ValidationError):
        ValidationResult(reason_code="test", sandbox_revision="vulnerable", **payload)
