"""Export runtime JSON schemas and OpenAPI without starting services."""

import importlib.util
import json
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]


def load(name, service):
    path = ROOT / "services" / service / "app"
    spec = importlib.util.spec_from_file_location(
        name, path / "__init__.py", submodule_search_locations=[str(path)]
    )
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return __import__(name + ".main", fromlist=["app"])


def main():
    graph = load("graph_contract", "graph-risk-service")
    ai = load("ai_contract", "ai-validation-service")
    from ai_contract.reasoning import Explanation
    from ai_contract.validation import ValidationRequest, ValidationResult
    from graph_contract.models import AnalysisResult, RiskResult
    from graph_contract.policy import Policy

    for name, model in [
        ("ir/risk-policy", Policy),
        ("ir/graph-analysis", AnalysisResult),
        ("ir/risk-result", RiskResult),
        ("ai/explanation", Explanation),
        ("validation/sandbox-request", ValidationRequest),
        ("validation/sandbox-result", ValidationResult),
    ]:
        document = model.model_json_schema()
        document["$schema"] = "https://json-schema.org/draft/2020-12/schema"
        document["$id"] = "https://riskgraph.ai/contracts/" + name + ".schema.json"
        (ROOT / f"contracts/{name}.schema.json").write_text(
            json.dumps(document, indent=2) + "\n", encoding="utf-8", newline="\n"
        )
    for name, app in [("graph-risk-api", graph.app), ("ai-validation-api", ai.app)]:
        (ROOT / f"contracts/api/{name}.openapi.yaml").write_text(
            yaml.safe_dump(app.openapi(), sort_keys=False), encoding="utf-8", newline="\n"
        )
    schema = json.loads((ROOT / "contracts/ir/graph-analysis.schema.json").read_text())
    properties = schema["properties"]
    for name in (
        "scan_id",
        "analysis_id",
        "analyzer_version",
        "analyzer_config_hash",
        "status",
        "pre_validation_verdict",
        "final_verdict",
        "validation_status",
    ):
        properties[name] = {"type": "string"}
    json.loads((ROOT / "contracts/ir/analysis-envelope.schema.json").read_text())
    # Preserve analyzer metadata without mixing it into canonical endpoint records.
    for name in ("provenance", "coverage", "diagnostics"):
        properties[name] = {"$ref": "analysis-envelope.schema.json#/properties/" + name}
    properties["source_evidence"] = {
        "type": "object",
        "additionalProperties": False,
        "required": ["before", "after"],
        "properties": {
            revision: {"$ref": "analysis-envelope.schema.json#/$defs/evidenceList"}
            for revision in ("before", "after")
        },
    }
    properties["ai"] = {"$ref": "../ai/explanation.schema.json"}
    properties["sandbox_demonstration"] = {"$ref": "../validation/sandbox-result.schema.json"}
    properties["validation"] = {"$ref": "../validation/sandbox-result.schema.json"}
    properties["findings"] = {
        "type": "array",
        "items": {
            "type": "object",
            "additionalProperties": False,
            "required": [
                "finding_id",
                "route_id",
                "method",
                "path",
                "resource",
                "severity",
                "dependency_path",
                "evidence",
                "ai",
                "validation",
            ],
            "properties": {
                "finding_id": {"type": "string", "pattern": "^[0-9a-f]{64}$"},
                "route_id": {"type": "string", "minLength": 1},
                "method": {"enum": ["GET", "POST", "PUT", "PATCH", "DELETE"]},
                "path": {"type": "string", "pattern": "^/"},
                "resource": {"type": "string", "minLength": 1},
                "severity": {"enum": ["LOW", "MODERATE", "MEDIUM", "HIGH", "CRITICAL"]},
                "dependency_path": {"$ref": "#/$defs/PathEvidence"},
                "evidence": {"type": "array", "minItems": 1, "items": {"type": "string"}},
                "ai": {"$ref": "../ai/explanation.schema.json"},
                "validation": {"$ref": "../validation/sandbox-result.schema.json"},
            },
        },
    }
    for name in ("pre_validation_verdict", "final_verdict"):
        properties[name] = {"enum": ["ALLOW", "REVIEW", "BLOCK"]}
    properties["validation_status"] = {
        "enum": ["CONFIRMED", "REJECTED", "INCONCLUSIVE", "NOT_RUN", "ERROR"]
    }
    properties["status"] = {"enum": ["COMPLETE", "DEGRADED"]}
    schema["required"] += [
        "scan_id",
        "analysis_id",
        "analyzer_config_hash",
        "provenance",
        "source_evidence",
        "coverage",
        "diagnostics",
        "pre_validation_verdict",
        "final_verdict",
        "validation_status",
        "status",
        "findings",
    ]
    schema["$id"] = "https://riskgraph.ai/contracts/ir/scan-result.schema.json"
    (ROOT / "contracts/ir/scan-result.schema.json").write_text(
        json.dumps(schema, indent=2) + "\n", encoding="utf-8", newline="\n"
    )
    platform_path = ROOT / "contracts/api/platform-api.openapi.yaml"
    platform = yaml.safe_load(platform_path.read_text())
    platform["info"]["version"] = "1.0.0"
    request = {
        "type": "object",
        "additionalProperties": False,
        "required": ["repository_path", "old_commit", "new_commit"],
        "properties": {
            "repository_path": {"type": "string", "minLength": 1},
            **{
                k: {"type": "string", "pattern": "^[0-9a-fA-F]{40}$"}
                for k in ("old_commit", "new_commit")
            },
        },
    }
    success = {
        "description": "Versioned deterministic scan with provenance and optional unconfirmed AI",
        "content": {"application/json": {"schema": {"$ref": "../ir/scan-result.schema.json"}}},
    }
    error = {"description": "Structured failure; never implies ALLOW"}
    platform["paths"]["/analyses"] = {
        "post": {
            "summary": "Analyze an allowlisted local immutable commit pair",
            "requestBody": {"required": True, "content": {"application/json": {"schema": request}}},
            "responses": {
                "200": success,
                **{code: error for code in ("400", "403", "502", "503", "504")},
            },
        }
    }
    platform["paths"]["/analyses/{id}"] = {
        "get": {
            "parameters": [
                {"in": "path", "name": "id", "required": True, "schema": {"type": "string"}}
            ],
            "responses": {"200": success, "404": error},
        }
    }
    platform["paths"]["/analyses/{id}/validation"] = {
        "post": {
            "summary": "Validate a registered source-bound Docker fixture",
            "parameters": [
                {"in": "path", "name": "id", "required": True, "schema": {"type": "string"}}
            ],
            "responses": {"200": success, "400": error},
        }
    }
    platform["paths"]["/analyses/{id}/sandbox-demonstration/{revision}"] = {
        "post": {
            "summary": "Run the shipped synthetic sandbox separately from source confirmation",
            "parameters": [
                {"in": "path", "name": name, "required": True, "schema": {"type": "string"}}
                for name in ("id", "revision")
            ],
            "responses": {"200": success, "400": error},
        }
    }
    schemas = platform["components"]["schemas"]
    for model in ("ScanResult", "CreateScanResponse"):
        schemas[model]["properties"]["status"]["enum"] = [
            "PENDING",
            "RUNNING",
            "COMPLETE",
            "DEGRADED",
            "FAILED",
        ]
    for name in ("old_commit", "new_commit"):
        schemas["CreateScanRequest"]["properties"][name] = {
            "type": "string",
            "pattern": "^[0-9a-fA-F]{40}$",
        }
    demo = json.loads((ROOT / "contracts/ir/graph-analysis.schema.json").read_text())
    # Use an external self-contained document so its local $defs resolve correctly.
    demo["properties"].update(
        {
            "mode": {"type": "string"},
            "pre_validation_verdict": {"type": "string"},
            "final_verdict": {"type": "string"},
            "validation_status": {"type": "string"},
        }
    )
    (ROOT / "contracts/ir/demo-result.schema.json").write_text(
        json.dumps(demo, indent=2) + "\n", encoding="utf-8", newline="\n"
    )
    schemas["DemoAnalysisResult"] = {"$ref": "../ir/demo-result.schema.json"}
    platform["paths"]["/demo/scenarios/{scenario}"] = {
        "get": {
            "parameters": [
                {"in": "path", "name": "scenario", "required": True, "schema": {"type": "string"}}
            ],
            "responses": platform["paths"]["/demo/authorization-removal"]["get"]["responses"],
        }
    }
    # Repair a pre-existing misplaced property in the legacy finding summary.
    finding = schemas["ScanResult"]["properties"]["findings"]["items"]["properties"]
    finding["severity"].pop("description", None)
    finding["description"] = {"type": "string"}
    finding["finding_id"] = {"type": "string", "pattern": "^[0-9a-f]{64}$"}
    required = schemas["ScanResult"]["properties"]["findings"]["items"].setdefault("required", [])
    if "finding_id" not in required:
        required.append("finding_id")
    platform["info"]["version"] = "2.0.0"
    platform["info"]["description"] = (
        "Source and account APIs require a platform session. POST operations also require the CSRF token "
        "returned by /auth/csrf. The endpoint IR shape remains compatible; the source-analysis envelope is version 1.1.0."
    )
    platform["components"]["securitySchemes"] = {
        "sessionCookie": {"type": "apiKey", "in": "cookie", "name": "JSESSIONID"}
    }
    platform["security"] = [{"sessionCookie": []}]
    profile = {
        "type": "object",
        "required": ["username", "name", "role"],
        "additionalProperties": False,
        "properties": {
            "username": {"type": "string", "pattern": "^[a-z][a-z0-9_.-]{2,63}$"},
            "name": {"type": "string", "minLength": 1, "maxLength": 120},
            "role": {"type": "string", "enum": ["DEVELOPER", "ANALYST", "ADMIN"]},
        },
    }
    schemas["PlatformAccount"] = profile
    schemas["NewPlatformAccount"] = {
        **profile,
        "required": [*profile["required"], "password"],
        "properties": {
            **profile["properties"],
            "password": {
                "type": "string",
                "minLength": 12,
                "maxLength": 72,
                "writeOnly": True,
                "description": "Maximum 72 UTF-8 bytes; BCrypt stored, never returned",
            },
        },
    }

    def json_response(schema):
        return {"description": "Success", "content": {"application/json": {"schema": schema}}}

    platform["paths"]["/auth/csrf"] = {
        "get": {
            "security": [],
            "summary": "Start/reuse session and obtain CSRF token",
            "responses": {
                "200": json_response(
                    {
                        "type": "object",
                        "required": ["headerName", "token"],
                        "properties": {
                            "headerName": {"const": "X-CSRF-TOKEN"},
                            "token": {"type": "string"},
                        },
                    }
                )
            },
        }
    }
    platform["paths"]["/auth/session"] = {
        "get": {
            "security": [],
            "summary": "Read current account; anonymous sessions return authenticated=false",
            "responses": {
                "200": json_response(
                    {
                        "type": "object",
                        "required": ["authenticated"],
                        "properties": {
                            "authenticated": {"type": "boolean"},
                            "user": {"$ref": "#/components/schemas/PlatformAccount"},
                        },
                    }
                )
            },
        }
    }
    platform["paths"]["/auth/login"] = {
        "post": {
            "security": [],
            "summary": "Sign in; fetch a fresh CSRF token after success",
            "requestBody": {
                "required": True,
                "content": {
                    "application/x-www-form-urlencoded": {
                        "schema": {
                            "type": "object",
                            "required": ["username", "password"],
                            "properties": {
                                "username": {"type": "string"},
                                "password": {"type": "string", "writeOnly": True},
                            },
                        }
                    }
                },
            },
            "responses": {
                "200": {"description": "Signed in; session ID rotated"},
                "401": {"description": "Invalid credentials"},
                "429": {"description": "Too many failed login attempts; retry later"},
            },
        }
    }
    platform["paths"]["/auth/logout"] = {
        "post": {
            "summary": "Invalidate session",
            "responses": {"200": {"description": "Signed out"}},
        }
    }
    platform["paths"]["/admin/users"] = {
        "get": {
            "summary": "List accounts (Admin only)",
            "responses": {
                "200": json_response(
                    {"type": "array", "items": {"$ref": "#/components/schemas/PlatformAccount"}}
                )
            },
        },
        "post": {
            "summary": "Create account (Admin only)",
            "requestBody": {
                "required": True,
                "content": {
                    "application/json": {
                        "schema": {"$ref": "#/components/schemas/NewPlatformAccount"}
                    }
                },
            },
            "responses": {
                "200": json_response({"$ref": "#/components/schemas/PlatformAccount"}),
                "400": error,
                "409": {"description": "Username already exists"},
            },
        },
    }
    for path, operations in platform["paths"].items():
        for method, operation in operations.items():
            if method not in ("get", "post"):
                continue
            if path == "/health" or path.startswith("/demo/"):
                operation["security"] = []
            if method == "post":
                operation["parameters"] = [
                    p for p in operation.get("parameters", []) if p.get("name") != "X-CSRF-TOKEN"
                ]
                operation["parameters"].append(
                    {
                        "in": "header",
                        "name": "X-CSRF-TOKEN",
                        "required": True,
                        "schema": {"type": "string"},
                    }
                )
            if operation.get("security") != []:
                operation["responses"]["401"] = {"description": "Authentication required"}
            operation["responses"]["403"] = {
                "description": "Insufficient role, disallowed origin, or invalid CSRF token"
            }
    platform_path.write_text(
        yaml.safe_dump(platform, sort_keys=False), encoding="utf-8", newline="\n"
    )


if __name__ == "__main__":
    main()
