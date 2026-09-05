import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

import yaml
from jsonschema import Draft202012Validator


ROOT = Path(__file__).resolve().parents[2]


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def validate_schema(schema_path: Path, example_path: Path) -> None:
    validator = Draft202012Validator(load_json(schema_path))
    errors = sorted(validator.iter_errors(load_json(example_path)), key=lambda error: error.path)
    if errors:
        print(f"FAIL {example_path}")
        for error in errors:
            path = ".".join(str(part) for part in error.path) or "<root>"
            print(f"  {path}: {error.message}")
        raise SystemExit(1)
    print(f"OK {example_path.relative_to(ROOT)}")


def validate_contracts() -> None:
    endpoint_schema = ROOT / "contracts" / "ir" / "endpoint-ir.schema.json"
    for example in sorted((ROOT / "contracts" / "ir" / "examples").glob("*.json")):
        validate_schema(endpoint_schema, example)

    validate_schema(
        ROOT / "contracts" / "ai" / "ai-analysis.schema.json",
        ROOT / "contracts" / "ai" / "examples" / "authorization-bypass-analysis.json",
    )
    validate_schema(
        ROOT / "contracts" / "ai" / "ai-test-suggestion.schema.json",
        ROOT / "contracts" / "ai" / "examples" / "authorization-bypass-test-suggestion.json",
    )
    validate_schema(
        ROOT / "contracts" / "validation" / "http-validation-test.schema.json",
        ROOT / "contracts" / "validation" / "examples" / "http-validation-test.json",
    )
    validate_schema(
        ROOT / "contracts" / "validation" / "validation-result.schema.json",
        ROOT / "contracts" / "validation" / "examples" / "validation-result.json",
    )


def validate_yaml() -> None:
    yaml_files = sorted((ROOT / "contracts" / "api").glob("*.yaml"))
    yaml_files.append(ROOT / "infrastructure" / "docker-compose.yml")
    for yaml_path in yaml_files:
        with yaml_path.open("r", encoding="utf-8") as file:
            parsed = yaml.safe_load(file)
        if not isinstance(parsed, dict):
            raise SystemExit(f"FAIL {yaml_path.relative_to(ROOT)} did not parse as a mapping")
        print(f"OK {yaml_path.relative_to(ROOT)}")


def docker_command() -> list[str] | None:
    docker = shutil.which("docker")
    if docker:
        return [docker]

    user_profile = os.environ.get("USERPROFILE")
    if user_profile:
        docker_path = Path(user_profile) / "AppData" / "Local" / "Programs" / "DockerDesktop" / "resources" / "bin" / "docker.exe"
        if docker_path.exists():
            return [str(docker_path)]

    return None


def validate_compose() -> None:
    docker = docker_command()
    if not docker:
        print("SKIP Docker Compose config: docker executable not found")
        return

    compose_file = ROOT / "infrastructure" / "docker-compose.yml"
    commands = [
        docker + ["compose", "-f", str(compose_file), "config"],
        docker + ["compose", "-f", str(compose_file), "--profile", "future-services", "config"],
        docker + ["compose", "-f", str(compose_file), "--profile", "sandbox", "config"],
    ]
    for command in commands:
        subprocess.run(command, cwd=ROOT, check=True, stdout=subprocess.DEVNULL)
        print(f"OK {' '.join(command[-4:])}")


def main() -> None:
    validate_contracts()
    validate_yaml()
    validate_compose()
    print("Week 2 verification passed")


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as error:
        print(f"FAIL command exited {error.returncode}: {' '.join(error.cmd)}")
        sys.exit(error.returncode)
