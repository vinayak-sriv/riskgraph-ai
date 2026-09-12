import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def run(*command: str) -> None:
    executable = shutil.which(command[0])
    if executable is None:
        raise SystemExit(f"Required executable not found: {command[0]}")
    subprocess.run((executable, *command[1:]), cwd=ROOT, check=True)


def main() -> None:
    with tempfile.TemporaryDirectory(prefix="riskgraph-week6-") as directory:
        run(sys.executable, "tools/dev/create_week6_sample.py", "--output", directory + "/sample")
    run("mvn", "-f", "services/java-analyzer/pom.xml", "verify")
    run(sys.executable, "-m", "pytest", "tests/contract", "-q")
    print("Weeks 4-6 verification passed")


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as error:
        raise SystemExit(error.returncode) from error
