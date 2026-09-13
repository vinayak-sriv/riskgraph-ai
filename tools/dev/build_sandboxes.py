"""Build only the authored authorization-removal fixture with a fixed trusted POM.
Never invokes build scripts from an arbitrary analyzed repository.
"""

import os
import shutil
import subprocess
import sys
from pathlib import Path

from create_mvp_samples import CONTROLLER, FILES, ROOT, SOURCE, create, git

PROBE_IMAGE = os.environ.get(
    "RISKGRAPH_VALIDATION_PROBE_IMAGE",
    "python:3.12.14-alpine3.24@sha256:b64631e04e4920160c50fbe8d8df828f7f35f06f425cb44aa09bca53e708a35a",
)

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
        subprocess.run([*docker, "pull", PROBE_IMAGE], check=True)


if __name__ == "__main__":
    main()
