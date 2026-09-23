"""Build only the authored authorization-removal fixture with a fixed trusted POM.
Never invokes build scripts from an arbitrary analyzed repository.
"""

import hashlib
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

from create_mvp_samples import CONTROLLER, FILES, ROOT, SOURCE, create, git
from create_mvp_samples_python import FILES as FILES_PY
from create_mvp_samples_python import HANDLER_PROTECTED, HANDLER_VULNERABLE
from create_mvp_samples_python import SOURCE as SOURCE_PY
from create_mvp_samples_python import create as create_python

DEFAULT_PROBE_IMAGE = (
    "python:3.12.14-alpine3.24@"
    "sha256:b64631e04e4920160c50fbe8d8df828f7f35f06f425cb44aa09bca53e708a35a"
)
APPROVED_PROBE_IMAGES = frozenset({DEFAULT_PROBE_IMAGE})
SANDBOX_IMAGES = (
    "riskgraph-sandbox-protected:local",
    "riskgraph-sandbox-vulnerable:local",
    "riskgraph-sandbox:local",
    "riskgraph-sandbox-protected-py:local",
    "riskgraph-sandbox-vulnerable-py:local",
)
LOCAL_PROBE_IMAGE = "riskgraph-validation-probe:local"


def approved_probe_image(configured: str | None = None) -> str:
    image = configured or os.environ.get("RISKGRAPH_VALIDATION_PROBE_IMAGE", DEFAULT_PROBE_IMAGE)
    if image not in APPROVED_PROBE_IMAGES:
        raise SystemExit("RISKGRAPH_VALIDATION_PROBE_IMAGE is not an approved digest-pinned image")
    return image


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def register_python_sandbox(pair: dict) -> None:
    """Add the trusted Python pair to the source-validation registry."""
    registry_dir = ROOT / "samples/generated/mvp-v2"
    targets = (
        (registry_dir / "manifest.json", pair["repository_path"]),
        (
            registry_dir / "manifest.compose.json",
            "/analysis-repositories/mvp-v2-python/authorization-removal",
        ),
    )
    for path, repository_path in targets:
        manifest = json.loads(path.read_text(encoding="utf-8"))
        manifest["scenarios"]["authorization-removal-python"] = {
            "repository_path": repository_path,
            "old_commit": pair["old_commit"],
            "new_commit": pair["new_commit"],
        }
        path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8", newline="\n")


def archive_validation_images(docker: list[str], probe_image: str) -> Path:
    """Create an atomic, ignored archive for the network-isolated Docker daemon."""

    output = ROOT / "tmp/validation-images/images.tar"
    staging = output.with_suffix(".tar.part")
    checksum = output.with_name("images.sha256")
    checksum_staging = checksum.with_suffix(".part")
    output.parent.mkdir(parents=True, exist_ok=True)
    staging.unlink(missing_ok=True)
    checksum_staging.unlink(missing_ok=True)
    try:
        for image in [*SANDBOX_IMAGES, probe_image]:
            subprocess.run(
                [*docker, "image", "inspect", image],
                check=True,
                stdout=subprocess.DEVNULL,
            )
        subprocess.run([*docker, "image", "tag", probe_image, LOCAL_PROBE_IMAGE], check=True)
        subprocess.run(
            [*docker, "save", "--output", str(staging), *SANDBOX_IMAGES, LOCAL_PROBE_IMAGE],
            check=True,
        )
        checksum_staging.write_text(file_sha256(staging) + "\n", encoding="ascii", newline="\n")
        staging.replace(output)
        checksum_staging.replace(checksum)
    finally:
        staging.unlink(missing_ok=True)
        checksum_staging.unlink(missing_ok=True)
    return output


VALIDATION_SECURITY_HARNESS = """package demo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
class ValidationSecurityHarness {
    @Bean SecurityFilterChain validationFilters(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
    }
}
"""


def main():
    unknown = set(sys.argv[1:]) - {"--prepare-only"}
    if unknown:
        raise SystemExit(f"Unsupported arguments: {', '.join(sorted(unknown))}")
    prepare_only = "--prepare-only" in sys.argv[1:]
    configured_host = os.environ.get("RISKGRAPH_VALIDATION_DOCKER_HOST", "")
    if configured_host and configured_host != "tcp://validation-docker:2375":
        raise SystemExit("RISKGRAPH_VALIDATION_DOCKER_HOST is not allowlisted")
    docker = ["docker", *(["--host", configured_host] if configured_host else [])]
    probe_image = approved_probe_image()
    pair = create()["scenarios"]["authorization-removal"]
    repo = Path(pair["repository_path"])
    for revision, commit in [("protected", pair["old_commit"]), ("vulnerable", pair["new_commit"])]:
        context = ROOT / f"samples/generated/build-{revision}"
        context.mkdir(parents=True, exist_ok=True)
        expected = {
            **FILES,
            SOURCE + "ExportController.java": CONTROLLER
            if revision == "protected"
            else CONTROLLER.replace("    @PreAuthorize(\"hasRole('ADMIN')\")\n", ""),
        }
        for name, text in expected.items():
            actual = git(repo, "show", f"{commit}:{name}") + "\n"
            if actual != text:
                raise SystemExit("Sandbox source does not match the trusted fixture")
            target = context / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(text, encoding="utf-8", newline="\n")
        # The analyzed fixture stays annotation-only. This local-only harness
        # removes Spring Boot's default request authentication so the sandbox
        # can isolate and validate the method annotation hypothesis.
        harness = context / SOURCE / "ValidationSecurityHarness.java"
        harness.write_text(VALIDATION_SECURITY_HARNESS, encoding="utf-8", newline="\n")
        resources = context / "src/main/resources"
        resources.mkdir(parents=True, exist_ok=True)
        shutil.copy2(
            ROOT / "samples/sandbox/src/main/resources/application.properties",
            resources / "application.properties",
        )
        shutil.copy2(ROOT / "samples/sandbox/pom.xml", context / "pom.xml")
        dockerfile = (ROOT / "samples/sandbox/Dockerfile").read_text()
        dockerfile = dockerfile.replace(
            'LABEL ai.riskgraph.sandbox="1"',
            f'LABEL ai.riskgraph.sandbox="1" ai.riskgraph.commit="{commit}"',
        )
        (context / "Dockerfile").write_text(dockerfile, encoding="utf-8", newline="\n")
        if not prepare_only:
            subprocess.run(
                [*docker, "build", "--tag", f"riskgraph-sandbox-{revision}:local", str(context)],
                check=True,
            )
    py_pair = create_python()["scenarios"]["authorization-removal"]
    register_python_sandbox(py_pair)
    py_repo = Path(py_pair["repository_path"])
    py_handler_by_revision = {"protected": HANDLER_PROTECTED, "vulnerable": HANDLER_VULNERABLE}
    for revision, commit in [
        ("protected", py_pair["old_commit"]),
        ("vulnerable", py_pair["new_commit"]),
    ]:
        context = ROOT / f"samples/generated/build-{revision}-py"
        context.mkdir(parents=True, exist_ok=True)
        expected = {**FILES_PY, SOURCE_PY + "main.py": py_handler_by_revision[revision]}
        for name, text in expected.items():
            actual = git(py_repo, "show", f"{commit}:{name}") + "\n"
            if actual != text:
                raise SystemExit("Python sandbox source does not match the trusted fixture")
            target = context / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(text, encoding="utf-8", newline="\n")
        shutil.copy2(ROOT / "samples/sandbox-python/requirements.txt", context / "requirements.txt")
        dockerfile = (ROOT / "samples/sandbox-python/Dockerfile").read_text()
        dockerfile = dockerfile.replace(
            'LABEL ai.riskgraph.sandbox="1"',
            f'LABEL ai.riskgraph.sandbox="1" ai.riskgraph.commit="{commit}"',
        )
        (context / "Dockerfile").write_text(dockerfile, encoding="utf-8", newline="\n")
        if not prepare_only:
            subprocess.run(
                [
                    *docker,
                    "build",
                    "--tag",
                    f"riskgraph-sandbox-{revision}-py:local",
                    str(context),
                ],
                check=True,
            )
    if not prepare_only:
        subprocess.run(
            [*docker, "build", "--tag", "riskgraph-sandbox:local", str(ROOT / "samples/sandbox")],
            check=True,
        )
        subprocess.run([*docker, "pull", probe_image], check=True)
        archive = archive_validation_images(docker, probe_image)
        print(f"Validation image archive: {archive}")


if __name__ == "__main__":
    main()
