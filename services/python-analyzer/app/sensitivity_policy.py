"""Port of the Java analyzer's SensitivityPolicy (exact/prefix/suffix rule
matching with precedence, default fallback) for the Python analyzer's own
bundled policy file.

# ponytail: only exact/prefix/suffix matching is ported. The Java engine also
# supports a `resources` exact-match shortcut and `regex` rules, but the
# bundled policy (app/resources/sensitivity-policy.yml, a copy of the Java
# one) uses neither. Add regex/resources support if a future policy edit
# needs it.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import yaml

_ALLOWED = {"LOW", "MODERATE", "MEDIUM", "HIGH", "CRITICAL"}
_KIND_PRECEDENCE = {"exact": 0, "prefix": 1, "suffix": 1}
_DEFAULT_POLICY_PATH = Path(__file__).parent / "resources" / "sensitivity-policy.yml"


@dataclass(frozen=True)
class Classification:
    sensitivity: str
    source: str
    matched_rule: str
    match_kind: str
    defaulted: bool


@dataclass(frozen=True)
class _Rule:
    id: str
    sensitivity: str
    kind: str  # "exact" | "prefix" | "suffix"
    expression: str  # already lowercased
    order: int

    def matches(self, resource: str) -> bool:
        if self.kind == "exact":
            return resource == self.expression
        if self.kind == "prefix":
            return resource.startswith(self.expression)
        return resource.endswith(self.expression)


class SensitivityPolicy:
    def __init__(self, policy_path: Path | str | None = None):
        path = Path(policy_path) if policy_path else _DEFAULT_POLICY_PATH
        with open(path, encoding="utf-8") as handle:
            document = yaml.safe_load(handle)
        self._version = _required(document, "version")
        self._default = _normalize(_required(document, "default_sensitivity"))
        self._unmatched_repository = (
            _normalize(document["unmatched_repository_sensitivity"])
            if document.get("unmatched_repository_sensitivity")
            else _at_least_medium(self._default)
        )
        self._rules = _load_rules(document.get("rules") or [])
        self.source = str(path)

    def classify(self, resource: str, repository_backed: bool = False) -> Classification:
        if not resource:
            raise ValueError("Resource name must be non-empty")
        normalized = resource.lower()
        match = min(
            (rule for rule in self._rules if rule.matches(normalized)),
            key=lambda rule: (_KIND_PRECEDENCE[rule.kind], -len(rule.expression), rule.order),
            default=None,
        )
        if match is not None:
            return Classification(
                match.sensitivity, self.source, f"{match.id}@{self._version}", match.kind, False
            )
        fallback = self._unmatched_repository if repository_backed else self._default
        return Classification(fallback, self.source, f"default@{self._version}", "default", True)


def _required(document: dict, key: str) -> str:
    value = document.get(key)
    if not value:
        raise ValueError(f"Sensitivity policy is missing {key}")
    return str(value)


def _normalize(sensitivity: str) -> str:
    normalized = str(sensitivity).upper()
    if normalized not in _ALLOWED:
        raise ValueError(f"Unsupported sensitivity: {sensitivity}")
    return normalized


def _at_least_medium(sensitivity: str) -> str:
    return "MEDIUM" if sensitivity in {"LOW", "MODERATE"} else sensitivity


def _load_rules(configured_rules: list[dict]) -> list[_Rule]:
    rules: list[_Rule] = []
    for configured in configured_rules:
        rule_id = _required(configured, "id")
        sensitivity = _normalize(_required(configured, "sensitivity"))
        for field, kind in (("exact", "exact"), ("prefix", "prefix"), ("suffix", "suffix")):
            for expression in _as_list(configured.get(field)):
                rules.append(_Rule(rule_id, sensitivity, kind, expression.lower(), len(rules)))
    if not rules:
        raise ValueError("Sensitivity policy must define at least one rule")
    return rules


def _as_list(value) -> list[str]:
    if value is None:
        return []
    return [value] if isinstance(value, str) else list(value)
