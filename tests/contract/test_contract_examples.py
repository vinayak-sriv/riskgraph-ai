import json
from pathlib import Path

import yaml
from jsonschema import Draft202012Validator


ROOT = Path(__file__).resolve().parents[2]


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def assert_valid(schema_path: Path, example_path: Path) -> None:
    schema = load_json(schema_path)
    data = load_json(example_path)
    errors = sorted(Draft202012Validator(schema).iter_errors(data), key=lambda error: error.path)
    assert errors == []


def test_ir_examples_match_endpoint_ir_schema() -> None:
    schema_path = ROOT / "contracts" / "ir" / "endpoint-ir.schema.json"
    for example_path in sorted((ROOT / "contracts" / "ir" / "examples").glob("*.json")):
        assert_valid(schema_path, example_path)


def test_ai_examples_match_ai_schemas() -> None:
    assert_valid(
        ROOT / "contracts" / "ai" / "ai-analysis.schema.json",
        ROOT / "contracts" / "ai" / "examples" / "authorization-bypass-analysis.json",
    )
    assert_valid(
        ROOT / "contracts" / "ai" / "ai-test-suggestion.schema.json",
        ROOT / "contracts" / "ai" / "examples" / "authorization-bypass-test-suggestion.json",
    )


def test_validation_examples_match_validation_schemas() -> None:
    assert_valid(
        ROOT / "contracts" / "validation" / "http-validation-test.schema.json",
        ROOT / "contracts" / "validation" / "examples" / "http-validation-test.json",
    )
    assert_valid(
        ROOT / "contracts" / "validation" / "validation-result.schema.json",
        ROOT / "contracts" / "validation" / "examples" / "validation-result.json",
    )


def test_openapi_and_compose_yaml_parse() -> None:
    yaml_files = sorted((ROOT / "contracts" / "api").glob("*.yaml"))
    yaml_files.append(ROOT / "infrastructure" / "docker-compose.yml")

    for yaml_path in yaml_files:
        with yaml_path.open("r", encoding="utf-8") as file:
            parsed = yaml.safe_load(file)
        assert isinstance(parsed, dict), f"{yaml_path} should parse to a YAML mapping"


def test_platform_demo_fixtures_match_canonical_ir_examples() -> None:
    canonical = ROOT / "contracts" / "ir" / "examples"
    packaged = ROOT / "services" / "platform-api" / "src" / "main" / "resources" / "demo"

    for filename in ("auth-removal-before.json", "auth-removal-after.json"):
        assert load_json(packaged / filename) == load_json(canonical / filename)
