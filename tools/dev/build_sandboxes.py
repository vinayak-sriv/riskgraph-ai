"""Build only the authored authorization-removal fixture with a fixed trusted POM.
Never invokes build scripts from an arbitrary analyzed repository.
"""

import os
import shutil
import subprocess
import sys
from pathlib import Path

from create_mvp_samples import CONTROLLER, FILES, ROOT, SOURCE, create, git

DEFAULT_PROBE_IMAGE = (
    "python:3.12.14-alpine3.24@"
    "sha256:b64631e04e4920160c50fbe8d8df828f7f35f06f425cb44aa09bca53e708a35a"
)
APPROVED_PROBE_IMAGES = frozenset({DEFAULT_PROBE_IMAGE})
SANDBOX_IMAGES = (
    "riskgraph-sandbox-protected:local",
    "riskgraph-sandbox-vulnerable:local",
    "riskgraph-sandbox:local",
)


def approved_probe_image(configured: str | None = None) -> str:
    image = configured or os.environ.get("RISKGRAPH_VALIDATION_PROBE_IMAGE", DEFAULT_PROBE_IMAGE)
    if image not in APPROVED_PROBE_IMAGES:
        raise SystemExit("RISKGRAPH_VALIDATION_PROBE_IMAGE is not an approved digest-pinned image")
    return image


def archive_validation_images(docker: list[str], probe_image: str) -> Path:
    """Create an atomic, ignored archive for the network-isolated Docker daemon."""

    output = ROOT / "tmp/validation-images/images.tar"
    staging = output.with_suffix(".tar.part")
    output.parent.mkdir(parents=True, exist_ok=True)
    staging.unlink(missing_ok=True)
    images = [*SANDBOX_IMAGES, probe_image]
    try:
        for image in images:
            subprocess.run([*docker, "image", "inspect", image], check=True)
        subprocess.run([*docker, "save", "--output", str(staging), *images], check=True)
        staging.replace(output)
    finally:
        staging.unlink(missing_ok=True)
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
