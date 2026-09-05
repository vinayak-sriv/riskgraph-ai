import json
import sys
from pathlib import Path

import pytest


ROOT = Path(__file__).resolve().parents[2]
SERVICE_ROOT = ROOT / "services" / "graph-risk-service"
sys.path.insert(0, str(SERVICE_ROOT))


@pytest.fixture
def authorization_removal_payload() -> dict:
    examples = ROOT / "contracts" / "ir" / "examples"
    return {
        "before": [json.loads((examples / "auth-removal-before.json").read_text(encoding="utf-8"))],
        "after": [json.loads((examples / "auth-removal-after.json").read_text(encoding="utf-8"))],
    }
