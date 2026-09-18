"""Heuristic handler -> service/repository -> resource resolution for
FastAPI routes (Track A batch 4, docs/pending-updates.md).

Ports the shape of the Java analyzer's DependencyPathResolver (BFS across
Spoon's fully resolved call graph) but not its mechanism: without a type
checker, Python source alone can't tell what class an attribute like
`self.x_service` is an instance of. So this only classifies the handler's
OWN direct calls by naming convention, in three families:
  - repository-object calls: `<name>_repository.<method>(...)` or a
    `<Name>Repository` attribute -- the resource is the name with the
    repository suffix stripped.
  - SQLAlchemy-style direct access: `<session>.query(Model)`,
    `<session>.get(Model, ...)`, `<session>.add(Model(...))`, `select(Model)`
    -- the resource is the model name. Recorded as repository-backed (HIGH
    confidence), same as a named repository, since the query itself IS the
    data-access point. Scoped to session-like base names (db/session/conn/
    *session) with a capitalized model argument, to avoid matching unrelated
    `.get(...)` calls (dict.get, request.headers.get, ...); a session-like
    call whose argument isn't a plain model reference is flagged ambiguous
    instead of guessed.
  - service-object calls: `<name>_service.<method>(...)` or a `<Name>Service`
    attribute -- recorded as a service-only path (no repository), same as
    the Java resolver's fallback for services that never reach a repository.

# ponytail: no same-file class/method recursion through service calls --
# that needs resolving which class a `self.x_service` attribute was
# constructed from (constructor annotations, FastAPI Depends() wiring),
# which is real type-inference work. Add it if batch 6's evaluation shows
# most repos route through an extra service layer this misses.
"""

from __future__ import annotations

import ast
from dataclasses import dataclass
from typing import NamedTuple

_ORM_METHODS = {"query", "get", "add"}
_SESSION_LIKE = {"db", "session", "db_session", "conn"}
_CONFIDENCE_ORDER = {"LOW": 0, "MEDIUM": 1, "HIGH": 2}


class DependencyPath(NamedTuple):
    service: str | None
    repository: str | None
    resource: str


@dataclass(frozen=True)
class Resolution:
    paths: list[DependencyPath]
    ambiguous: bool
    confidence: str  # LOW | MEDIUM | HIGH
    fallback_resource: str

    @property
    def primary(self) -> DependencyPath:
        return self.paths[0] if self.paths else DependencyPath(None, None, self.fallback_resource)


def resolve_dependencies(handler: ast.FunctionDef | ast.AsyncFunctionDef) -> Resolution:
    paths: dict[tuple, DependencyPath] = {}
    ambiguous = False

    for call in _direct_calls(handler):
        base, method = _callee(call)
        if base is not None and _looks_like(base, "repository"):
            resource = _strip_suffix(base, "repository")
            _record(paths, DependencyPath(None, base, resource))
        elif (method in _ORM_METHODS and _is_session_like(base)) or _is_select_call(call):
            arg = call.args[0] if call.args else None
            if arg is None:
                continue
            model = _model_name(arg)
            if model is not None:
                _record(paths, DependencyPath(None, model, model))
            else:
                ambiguous = True
        elif base is not None and _looks_like(base, "service"):
            resource = _strip_suffix(base, "service")
            _record(paths, DependencyPath(base, None, resource))

    ordered = sorted(
        paths.values(), key=lambda p: (p.service or "", p.repository or "", p.resource)
    )
    confidence = _confidence(ordered, ambiguous)
    return Resolution(ordered, ambiguous, confidence, fallback_resource=handler.name)


def confidence_minimum(*values: str) -> str:
    return min(values, key=lambda value: _CONFIDENCE_ORDER[value])


def _direct_calls(handler: ast.FunctionDef | ast.AsyncFunctionDef) -> list[ast.Call]:
    """Calls whose nearest enclosing function/lambda/class is `handler`
    itself -- mirrors the Java resolver's directInvocations(), which only
    follows invocations made directly by the current method."""
    calls: list[ast.Call] = []

    class _Visitor(ast.NodeVisitor):
        def visit_FunctionDef(self, node: ast.AST) -> None:
            pass  # do not descend into nested defs

        visit_AsyncFunctionDef = visit_FunctionDef
        visit_Lambda = visit_FunctionDef
        visit_ClassDef = visit_FunctionDef

        def visit_Call(self, node: ast.Call) -> None:
            calls.append(node)
            self.generic_visit(node)

    visitor = _Visitor()
    for statement in handler.body:
        visitor.visit(statement)
    return calls


def _callee(call: ast.Call) -> tuple[str | None, str | None]:
    if isinstance(call.func, ast.Attribute):
        return _base_name(call.func.value), call.func.attr
    return None, None


def _base_name(node: ast.expr) -> str | None:
    if isinstance(node, ast.Name):
        return node.id
    if isinstance(node, ast.Attribute):
        return node.attr
    return None


def _is_session_like(base: str | None) -> bool:
    if base is None:
        return False
    lowered = base.lower()
    return lowered in _SESSION_LIKE or lowered.endswith("session")


def _is_select_call(call: ast.Call) -> bool:
    return isinstance(call.func, ast.Name) and call.func.id == "select"


def _model_name(expr: ast.expr) -> str | None:
    if isinstance(expr, ast.Name) and expr.id[:1].isupper():
        return expr.id
    if isinstance(expr, ast.Attribute) and expr.attr[:1].isupper():
        return expr.attr
    if isinstance(expr, ast.Call):
        return _model_name(expr.func)
    return None


def _looks_like(name: str, kind: str) -> bool:
    return name.lower().endswith(kind)


def _strip_suffix(name: str, kind: str) -> str:
    lowered = name.lower()
    if lowered.endswith(f"_{kind}"):
        stripped = name[: -(len(kind) + 1)]
    elif lowered.endswith(kind):
        stripped = name[: -len(kind)]
    else:
        stripped = name
    # ponytail: mirrors the Java resolver's blank-resource guard -- a base
    # literally named "Repository"/"Service" strips to "", fall back to it.
    return stripped or name


def _record(paths: dict[tuple, DependencyPath], path: DependencyPath) -> None:
    paths.setdefault((path.service, path.repository, path.resource), path)


def _confidence(paths: list[DependencyPath], ambiguous: bool) -> str:
    if ambiguous:
        return "LOW"
    if any(path.repository is not None for path in paths):
        return "HIGH"
    return "MEDIUM" if paths else "LOW"
