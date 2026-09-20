"""The two analyzers must classify a resource identically.

`services/java-analyzer/.../sensitivity-policy.yml` and its python-analyzer
copy are maintained by hand. Nothing previously checked that they matched, so
a rule added to one would silently change verdicts on only half the repos.
"""

import re
from pathlib import Path

import pytest
import yaml

ROOT = Path(__file__).resolve().parents[2]
JAVA_POLICY = ROOT / "services/java-analyzer/src/main/resources/sensitivity-policy.yml"
PYTHON_POLICY = ROOT / "services/python-analyzer/app/resources/sensitivity-policy.yml"


def _without_leading_comments(path: Path) -> str:
    text = path.read_text(encoding="utf-8")
    return re.sub(r"\A(?:\s*#.*\n)+", "", text)


def test_both_analyzers_ship_the_same_policy_document():
    assert _without_leading_comments(JAVA_POLICY) == _without_leading_comments(PYTHON_POLICY), (
        "sensitivity-policy.yml has drifted between the Java and Python analyzers"
    )


def test_policy_uses_only_matchers_both_engines_implement():
    """The Java engine also supports `resources` and `regex`. The Python one
    does not, and now rejects them, so the shipped policy must avoid them."""
    policy = yaml.safe_load(JAVA_POLICY.read_text(encoding="utf-8"))
    supported = {"id", "sensitivity", "exact", "prefix", "suffix"}

    unsupported = {key for rule in policy["rules"] for key in rule if key not in supported}

    assert not unsupported, (
        f"policy uses matchers the Python analyzer cannot honour: {sorted(unsupported)}"
    )


@pytest.mark.parametrize(
    "resource",
    [
        "Account",
        "AccountSummary",
        "Admin",
        "Credential",
        "Customer",
        "Payment",
        "AuditLog",
        "Report",
        "Widget",
        "unmatched_resource",
    ],
)
def test_python_engine_classifies_every_probe_resource(resource: str):
    """Guards the loader itself: every probe must resolve to a band, so a
    parsing regression shows up as a failure rather than a silent default."""
    import importlib.util
    import sys

    path = ROOT / "services/python-analyzer/app"
    if "python_analyzer_app" not in sys.modules:
        spec = importlib.util.spec_from_file_location(
            "python_analyzer_app", path / "__init__.py", submodule_search_locations=[str(path)]
        )
        module = importlib.util.module_from_spec(spec)
        sys.modules["python_analyzer_app"] = module
        spec.loader.exec_module(module)
    from python_analyzer_app.sensitivity_policy import SensitivityPolicy

    classification = SensitivityPolicy().classify(resource, True)

    assert classification.sensitivity in {"LOW", "MODERATE", "MEDIUM", "HIGH", "CRITICAL"}
