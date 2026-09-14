"""Complete local quality gate. No publication or repository history changes."""

import json
import os
import shutil
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def main():
    output = ROOT / "tmp/release-checks"
    output.mkdir(parents=True, exist_ok=True)
    run_id = f"{os.getpid()}-{time.time_ns()}"
    commands = [
        (
            "python",
            [
                sys.executable,
                "-m",
                "pytest",
                "tests",
                "-q",
                "-o",
                f"cache_dir=tmp/pytest-cache-{run_id}",
                f"--basetemp=tmp/pytest-temp-{run_id}",
                "--cov=services/graph-risk-service/app",
                "--cov=services/ai-validation-service/app",
                "--cov-report=json:tmp/python-coverage.json",
                "--cov-report=term",
            ],
            ROOT,
        ),
        (
            "analyzer",
            ["mvn", "--batch-mode", "-f", "services/java-analyzer/pom.xml", "clean", "verify"],
            ROOT,
        ),
        (
            "python-lint",
            [
                "ruff",
                "check",
                "services/graph-risk-service",
                "services/ai-validation-service",
                "tests",
                "tools",
                "datasets/risk-corpus/tools",
            ],
            ROOT,
        ),
        (
            "python-format",
            [
                "ruff",
                "format",
                "--check",
                "services/graph-risk-service",
                "services/ai-validation-service",
                "tests",
                "tools",
                "datasets/risk-corpus/tools",
            ],
            ROOT,
        ),
        (
            "platform",
            ["mvn", "--batch-mode", "-f", "services/platform-api/pom.xml", "clean", "verify"],
            ROOT,
        ),
        ("frontend-install", ["npm", "ci"], ROOT / "apps/dashboard"),
        ("frontend-tests", ["npm", "test"], ROOT / "apps/dashboard"),
        ("frontend-build", ["npm", "run", "build"], ROOT / "apps/dashboard"),
        ("frontend-lint", ["npm", "run", "lint"], ROOT / "apps/dashboard"),
        ("frontend-format", ["npm", "run", "format:check"], ROOT / "apps/dashboard"),
        ("production-audit", ["npm", "audit", "--omit=dev"], ROOT / "apps/dashboard"),
        (
            "compose",
            [
                "docker",
                "compose",
                "-f",
                "infrastructure/docker-compose.yml",
                "-f",
                "infrastructure/docker-compose.validation.yml",
                "--profile",
                "app",
                "config",
                "--quiet",
            ],
            ROOT,
        ),
        (
            "corpus",
            [
                sys.executable,
                "datasets/risk-corpus/tools/corpus.py",
                "evaluate",
                "--output",
                "tmp/corpus-evaluation.json",
            ],
            ROOT,
        ),
        ("diff", ["git", "diff", "--check"], ROOT),
    ]
    results = []
    environment = os.environ.copy()
    environment.setdefault("RISKGRAPH_SERVICE_TOKEN", "release-check-only-not-a-deployment-secret")
    for name, command, cwd in commands:
        command[0] = shutil.which(command[0]) or command[0]
        print(f"Running {name}", flush=True)
        started = time.perf_counter()
        with (output / f"{name}.log").open("w", encoding="utf-8", newline="\n") as log:
            try:
                process = subprocess.run(
                    command,
                    cwd=cwd,
                    stdout=log,
                    stderr=subprocess.STDOUT,
                    timeout=600,
                    env=environment,
                    creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
                )
                code = process.returncode
            except (OSError, subprocess.TimeoutExpired) as ex:
                log.write(str(ex))
                code = -1
        results.append(
            dict(check=name, exit_code=code, seconds=round(time.perf_counter() - started, 2))
        )
        print(f"{name}: {'PASS' if code == 0 else 'FAIL'}", flush=True)
    (output / "results.json").write_text(
        json.dumps(results, indent=2) + "\n", encoding="utf-8", newline="\n"
    )
    if any(item["exit_code"] for item in results):
        raise SystemExit("One or more release checks failed; see tmp/release-checks")


if __name__ == "__main__":
    main()
