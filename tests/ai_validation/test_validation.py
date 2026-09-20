import asyncio
import json
import subprocess

import pytest
from ai_app.validation import (
    DEFAULT_PROBE_IMAGE,
    PRELOADED_PROBE_IMAGE,
    DockerRunner,
    ValidationRequest,
    ValidationResult,
    approved_probe_image,
    validate,
    validation_startup_seconds,
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
        self.daemon_mode = "test"
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
    result = FakeDocker("INCONCLUSIVE", cleanup_failure=True).validate(
        ValidationRequest(sandbox_revision="vulnerable")
    )
    assert result.status == "INCONCLUSIVE" and not result.confirmed
    assert not result.cleanup_complete and result.reason_code == "CLEANUP_FAILED"


def test_cleanup_failure_does_not_erase_a_confirmed_exploit():
    """A failed teardown says nothing about whether the probe reached the endpoint."""
    result = FakeDocker("CONFIRMED", cleanup_failure=True).validate(
        ValidationRequest(sandbox_revision="vulnerable")
    )
    assert result.status == "CONFIRMED" and result.confirmed
    assert not result.cleanup_complete
    assert "Sandbox teardown did not complete" in result.evidence


def test_commit_binding_rejects_mismatched_image_before_network_creation():
    runner = FakeDocker()
    result = runner.validate(
        ValidationRequest(sandbox_revision="vulnerable", expected_commit="b" * 40)
    )
    assert result.status == "ERROR" and not result.confirmed
    assert runner.calls == [["image", "inspect", "riskgraph-sandbox-vulnerable:local"]]


def test_python_language_selects_the_python_sandbox_image():
    runner = FakeDocker()
    runner.validate(
        ValidationRequest(
            sandbox_revision="vulnerable", language="python", expected_commit="b" * 40
        )
    )
    assert runner.calls == [["image", "inspect", "riskgraph-sandbox-vulnerable-py:local"]]


def test_language_defaults_to_java_for_existing_callers():
    assert ValidationRequest(sandbox_revision="protected").language == "java"


def test_missing_isolated_daemon_fails_closed_and_ignores_ambient_context(monkeypatch):
    monkeypatch.setenv("DOCKER_HOST", "tcp://example.com:2375")
    monkeypatch.setenv("DOCKER_CONTEXT", "remote")
    monkeypatch.delenv("RISKGRAPH_VALIDATION_DOCKER_HOST", raising=False)
    monkeypatch.delenv("RISKGRAPH_ALLOW_HOST_DOCKER", raising=False)
    monkeypatch.delenv("RISKGRAPH_RUNTIME_PROFILE", raising=False)
    with pytest.raises(ValueError, match="required"):
        DockerRunner()


def test_host_daemon_requires_both_local_profile_and_explicit_opt_in(monkeypatch):
    monkeypatch.delenv("RISKGRAPH_VALIDATION_DOCKER_HOST", raising=False)
    monkeypatch.setenv("RISKGRAPH_RUNTIME_PROFILE", "local")
    monkeypatch.setenv("RISKGRAPH_ALLOW_HOST_DOCKER", "true")
    command = DockerRunner().command
    assert command[:2] == ["docker", "--host"]
    assert command[2] in (
        "npipe:////./pipe/dockerDesktopLinuxEngine",
        "unix:///var/run/docker.sock",
    )


def test_only_dedicated_validation_daemon_can_be_configured(monkeypatch):
    monkeypatch.setenv("RISKGRAPH_VALIDATION_DOCKER_HOST", "tcp://attacker.example:2376")
    with pytest.raises(ValueError, match="not allowlisted"):
        DockerRunner()
    # the pre-TLS plaintext port is no longer an accepted destination
    monkeypatch.setenv("RISKGRAPH_VALIDATION_DOCKER_HOST", "tcp://validation-docker:2375")
    with pytest.raises(ValueError, match="not allowlisted"):
        DockerRunner()
    monkeypatch.setenv("RISKGRAPH_VALIDATION_DOCKER_HOST", "tcp://validation-docker:2376")
    command = DockerRunner().command
    assert "tcp://validation-docker:2376" in command


def test_isolated_daemon_client_requires_mutual_tls(monkeypatch):
    monkeypatch.setenv("RISKGRAPH_VALIDATION_DOCKER_HOST", "tcp://validation-docker:2376")
    monkeypatch.setenv("DOCKER_CERT_PATH", "/certs/client")
    command = DockerRunner().command

    assert "--tlsverify" in command
    assert "/certs/client/ca.pem" in command
    assert "/certs/client/cert.pem" in command
    assert "/certs/client/key.pem" in command


def test_isolated_mode_fails_closed_without_daemon(monkeypatch):
    monkeypatch.delenv("RISKGRAPH_VALIDATION_DOCKER_HOST", raising=False)
    monkeypatch.setenv("RISKGRAPH_RUNTIME_PROFILE", "production")
    monkeypatch.setenv("RISKGRAPH_ALLOW_HOST_DOCKER", "true")
    with pytest.raises(ValueError, match="required"):
        DockerRunner()


def test_unconfigured_validation_returns_sanitized_error(monkeypatch):
    monkeypatch.delenv("RISKGRAPH_VALIDATION_DOCKER_HOST", raising=False)
    monkeypatch.delenv("RISKGRAPH_RUNTIME_PROFILE", raising=False)
    monkeypatch.delenv("RISKGRAPH_ALLOW_HOST_DOCKER", raising=False)
    result = asyncio.run(validate(ValidationRequest(sandbox_revision="protected")))
    assert result.status == "ERROR"
    assert result.reason_code == "VALIDATION_DOCKER_NOT_CONFIGURED"


def test_operational_log_has_run_mode_exit_and_cleanup_without_response(caplog):
    runner = FakeDocker("CONFIRMED")
    with caplog.at_level("INFO", logger="riskgraph.validation"):
        runner.validate(ValidationRequest(sandbox_revision="vulnerable"))
    message = caplog.messages[-1]
    assert "operation=http-authorization-probe" in message
    assert "daemon_mode=test" in message
    assert "exit_category=confirmed" in message
    assert "cleanup_complete=True" in message
    assert "customer_export" not in message


def test_probe_image_must_match_the_digest_pinned_allowlist():
    assert approved_probe_image(DEFAULT_PROBE_IMAGE) == DEFAULT_PROBE_IMAGE
    assert approved_probe_image(PRELOADED_PROBE_IMAGE) == PRELOADED_PROBE_IMAGE
    with pytest.raises(ValueError, match="not an approved digest-pinned image"):
        approved_probe_image("python:latest")


def test_validation_startup_timeout_is_bounded_and_embedded_in_fixed_probe():
    assert validation_startup_seconds("45") == 45
    for invalid in ("4", "61", "not-a-number"):
        with pytest.raises(ValueError, match="timeout"):
            validation_startup_seconds(invalid)


@pytest.mark.parametrize(
    "payload",
    [
        {"status": "REJECTED", "confirmed": True, "actual_status": 403},
        {"status": "CONFIRMED", "confirmed": False, "actual_status": 200},
        {"status": "CONFIRMED", "confirmed": True, "actual_status": 403},
        {"status": "REJECTED", "confirmed": False, "actual_status": 200},
    ],
)
def test_contradictory_validation_results_are_rejected(payload):
    with pytest.raises(ValidationError):
        ValidationResult(reason_code="test", sandbox_revision="vulnerable", **payload)
