"""Build only the authored authorization-removal fixture with a fixed trusted POM.
Never invokes build scripts from an arbitrary analyzed repository.
"""

import os
import shutil
import subprocess
import sys
from pathlib import Path

from create_mvp_samples import CONTROLLER, FILES, ROOT, SOURCE, create, git


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
        subprocess.run([*docker, "pull", "python:3.12-alpine"], check=True)


if __name__ == "__main__":
    main()
