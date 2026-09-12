"""One fixed HTTP authorization probe in an isolated local Docker network.

No URL, image, command, host, port or credentials are accepted from AI/users.
Only the shipped synthetic sandbox and fixed GET /admin/export probe are supported.
"""

import asyncio
import json
import os
import subprocess
import uuid
from typing import Literal

from pydantic import Field, model_validator

from .reasoning import StrictModel

VALIDATION_CONCURRENCY = max(1, int(os.environ.get("RISKGRAPH_VALIDATION_CONCURRENCY", "2")))
VALIDATION_SEMAPHORE = asyncio.Semaphore(VALIDATION_CONCURRENCY)


class ValidationRequest(StrictModel):
    sandbox_revision: Literal["protected", "vulnerable"]
    expected_commit: str | None = Field(default=None, pattern=r"^[0-9a-f]{40}$")
    method: Literal["GET"] = "GET"
    path: Literal["/admin/export"] = "/admin/export"
    headers: dict[str, str] = Field(default_factory=dict, max_length=0)
    body: None = None
    auth_context: Literal["ANONYMOUS"] = "ANONYMOUS"


class ValidationResult(StrictModel):
    status: Literal["CONFIRMED", "REJECTED", "INCONCLUSIVE", "NOT_RUN", "ERROR"]
    confirmed: bool = False
    actual_status: int | None = None
    expected_status: int = 200
    evidence: list[str] = Field(default_factory=list)
    reason_code: str
    sandbox_revision: str
    container_image_id: str | None = None
    cleanup_complete: bool = True
    response_sha256: str | None = None
    source_commit: str | None = None

    @model_validator(mode="after")
    def consistent_outcome(self):
        if self.confirmed != (self.status == "CONFIRMED"):
            raise ValueError("confirmed must exactly match CONFIRMED status")
        if self.status == "CONFIRMED" and self.actual_status != 200:
            raise ValueError("confirmed validation requires HTTP 200")
        if self.status == "REJECTED" and self.actual_status not in (401, 403):
            raise ValueError("rejected validation requires HTTP 401 or 403")
        if not self.cleanup_complete and self.status != "ERROR":
            raise ValueError("incomplete cleanup must be reported as ERROR")
        return self


# Executed only in a new non-root probe container on the private internal network.
# The host never sends an HTTP security probe. Redirects are rejected.
PROBE = r"""
import hashlib,json,time,urllib.request,urllib.error
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs): return None
opener=urllib.request.build_opener(urllib.request.ProxyHandler({}),NoRedirect())
deadline=time.monotonic()+15
while True:
    try:
        try: response=opener.open('http://sandbox:8084/admin/export',timeout=2)
        except urllib.error.HTTPError as error: response=error
        with response:
            data=response.read(65537)
            status=response.code
        if len(data)>65536:
            print(json.dumps({'status':'INCONCLUSIVE','actual_status':status,'reason_code':'RESPONSE_TOO_LARGE'}))
        else:
            marker=False
            try: marker=json.loads(data)=={'customer_export':'sandbox-only'}
            except (ValueError,UnicodeError): pass
            result='REJECTED' if status in (401,403) else 'CONFIRMED' if status==200 and marker else 'INCONCLUSIVE'
            print(json.dumps({'status':result,'actual_status':status,'reason_code':'HTTP_AUTH_PROBE',
                'response_sha256':hashlib.sha256(data).hexdigest()}))
        break
    except (OSError,urllib.error.URLError):
        if time.monotonic()>=deadline:
            print(json.dumps({'status':'INCONCLUSIVE','actual_status':None,'reason_code':'SANDBOX_TIMEOUT'})); break
        time.sleep(.3)
"""


class DockerRunner:
    def __init__(self):
        # Never honor ambient DOCKER_HOST/context. Compose supplies the one
        # allowlisted, dedicated validation daemon address explicitly.
        configured = os.environ.get("RISKGRAPH_VALIDATION_DOCKER_HOST", "")
        if configured and configured != "tcp://validation-docker:2375":
            raise ValueError("Validation Docker host is not allowlisted")
        if configured:
            host = configured
        elif os.environ.get("RISKGRAPH_REQUIRE_ISOLATED_DOCKER", "false").lower() == "true":
            raise ValueError("Isolated validation Docker host is required")
        else:
            host = (
                "npipe:////./pipe/dockerDesktopLinuxEngine"
                if os.name == "nt"
                else "unix:///var/run/docker.sock"
            )
        self.command = ["docker", "--host", host]

    def run(self, args: list[str], timeout=5):
        result = subprocess.run(
            self.command + args,
            capture_output=True,
            text=True,
            timeout=timeout,
            check=True,
            creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
        )
        if len(result.stdout) > 131072:
            raise ValueError("Docker output limit")
        return result.stdout.strip()

    def validate(self, request: ValidationRequest) -> ValidationResult:
        suffix = uuid.uuid4().hex
        network, sandbox, probe = (f"riskgraph-{kind}-{suffix}" for kind in ("net", "app", "probe"))
        cleanup = True
        created = False
        image_id = None
        result = ValidationResult(
            status="ERROR",
            reason_code="DOCKER_UNAVAILABLE",
            sandbox_revision=request.sandbox_revision,
        )
        try:
            image_name = (
                f"riskgraph-sandbox-{request.sandbox_revision}:local"
                if request.expected_commit
                else "riskgraph-sandbox:local"
            )
            image = json.loads(self.run(["image", "inspect", image_name]))[0]
            if image.get("Config", {}).get("Labels", {}).get("ai.riskgraph.sandbox") != "1":
                raise ValueError("Untrusted sandbox image")
            image_id = image["Id"]
            source_commit = image.get("Config", {}).get("Labels", {}).get("ai.riskgraph.commit")
            if request.expected_commit and request.expected_commit != source_commit:
                raise ValueError("Sandbox commit identity mismatch")
            probe_image = json.loads(self.run(["image", "inspect", "python:3.12-alpine"]))[0]["Id"]
            created = True  # Include timeout-after-create cases in cleanup.
            self.run(
                [
                    "network",
                    "create",
                    "--internal",
                    "--label",
                    f"ai.riskgraph.run={suffix}",
                    network,
                ]
            )
            restrictions = [
                "--network",
                network,
                "--read-only",
                "--cap-drop=ALL",
                "--security-opt=no-new-privileges",
                "--user",
                "65532:65532",
                "--cpus=.5",
                "--memory=256m",
                "--pids-limit=64",
                "--tmpfs",
                "/tmp:rw,noexec,nosuid,size=32m",
                "--label",
                f"ai.riskgraph.run={suffix}",
            ]
            self.run(
                [
                    "run",
                    "--detach",
                    "--name",
                    sandbox,
                    "--network-alias",
                    "sandbox",
                    *restrictions,
                    "--env",
                    f"SPRING_PROFILES_ACTIVE={request.sandbox_revision}",
                    image_id,
                ]
            )
            raw = self.run(
                ["run", "--name", probe, *restrictions, probe_image, "python", "-c", PROBE],
                timeout=25,
            )
            payload = json.loads(raw)
            result = ValidationResult(
                **payload,
                confirmed=payload["status"] == "CONFIRMED",
                sandbox_revision=request.sandbox_revision,
                container_image_id=image_id,
                source_commit=source_commit,
                evidence=[
                    "Anonymous GET /admin/export executed in the shipped local Docker sandbox"
                ],
            )
        except subprocess.TimeoutExpired:
            result.reason_code = "DOCKER_TIMEOUT"
        except (subprocess.SubprocessError, OSError, ValueError, KeyError):
            result.reason_code = "DOCKER_UNAVAILABLE_OR_INVALID"
        finally:
            if created:
                for name in (probe, sandbox):
                    try:
                        # Removing our unique names also covers partially completed docker run.
                        self.run(["rm", "--force", name], timeout=5)
                    except subprocess.CalledProcessError as error:
                        if "No such container" not in (error.stderr or ""):
                            cleanup = False
                    except (subprocess.SubprocessError, OSError, ValueError):
                        cleanup = False
                try:
                    self.run(["network", "rm", network], timeout=5)
                except subprocess.CalledProcessError as error:
                    if "not found" not in (error.stderr or ""):
                        cleanup = False
                except (subprocess.SubprocessError, OSError, ValueError):
                    cleanup = False
        result.cleanup_complete = cleanup
        if not cleanup:
            result.status, result.confirmed, result.reason_code = "ERROR", False, "CLEANUP_FAILED"
        return result


async def validate(request: ValidationRequest) -> ValidationResult:
    async with VALIDATION_SEMAPHORE:
        return await asyncio.to_thread(DockerRunner().validate, request)
