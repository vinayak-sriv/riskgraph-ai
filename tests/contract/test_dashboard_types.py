"""`apps/dashboard/src/types.ts` is a hand-maintained view of the contracts.

It had already drifted: three schema properties were missing entirely,
including `coverage`. Nothing checked it, so adding a field to a Pydantic
model left the dashboard compiling cleanly while silently ignoring it. This
keeps the check mechanical without a codegen toolchain.
"""

import json
import re
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
TYPES = ROOT / "apps/dashboard/src/types.ts"

# schema file -> the TypeScript type that models it
MODELLED = {
    "contracts/ir/scan-result.schema.json": "AnalysisResult",
    "contracts/ir/demo-result.schema.json": "AnalysisResult",
}

# Present in the schema but deliberately not surfaced in the dashboard type.
NOT_MODELLED: dict[str, set[str]] = {}


def _declared_fields(type_name: str) -> set[str]:
    source = TYPES.read_text(encoding="utf-8")
    match = re.search(rf"export type {type_name} = \{{(.*?)\n\}};", source, re.DOTALL)
    assert match, f"{type_name} not found in types.ts"
    # Top-level keys only: nested object literals are indented further.
    return set(re.findall(r"^  (\w+)\??:", match.group(1), re.MULTILINE))


@pytest.mark.parametrize("schema_path,type_name", sorted(MODELLED.items()))
def test_dashboard_type_declares_every_schema_property(schema_path: str, type_name: str):
    schema = json.loads((ROOT / schema_path).read_text(encoding="utf-8"))
    expected = set(schema.get("properties", {})) - NOT_MODELLED.get(schema_path, set())

    missing = expected - _declared_fields(type_name)

    assert not missing, (
        f"{type_name} in types.ts is missing {sorted(missing)} from {schema_path}; "
        "add them (optional is fine) or list them in NOT_MODELLED with a reason"
    )


def test_every_required_scan_field_is_declared():
    """Required fields are the ones a consumer may assume exist."""
    schema = json.loads((ROOT / "contracts/ir/scan-result.schema.json").read_text(encoding="utf-8"))

    missing = set(schema["required"]) - _declared_fields("AnalysisResult")

    assert not missing, f"AnalysisResult omits required scan-result fields: {sorted(missing)}"
