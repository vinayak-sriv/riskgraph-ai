"""FastAPI route/handler/auth-dependency extraction from Python AST.

Track A batch 3 extracts route decorators (HTTP method + path), the handler
function, and whether the handler takes a FastAPI `Depends(...)` parameter
(authentication evidence). Batch 4 adds handler -> service/repository ->
resource resolution via resolver.py (see its module docstring for the exact
heuristic scope) and sensitivity classification via sensitivity_policy.py.

# ponytail: router-prefix stitching (`app.include_router(router,
# prefix="/api")` across files) is still not resolved, so a route mounted
# under a prefix is reported with its bare in-file path only. Upgrade path:
# build a cross-file include_router graph if batch 5/6 evaluation shows
# prefixed routes are common in the pinned repos.
"""

import ast
from functools import lru_cache

from .resolver import Resolution, confidence_minimum, resolve_dependencies
from .sensitivity_policy import SensitivityPolicy

_HTTP_METHODS = {"get", "post", "put", "patch", "delete"}


@lru_cache(maxsize=1)
def _get_policy() -> SensitivityPolicy:
    return SensitivityPolicy()


def extract_endpoints(source: str, file_path: str) -> list[dict]:
    try:
        tree = ast.parse(source, filename=file_path)
    except SyntaxError:
        return []

    router_names = _router_variable_names(tree)
    policy = _get_policy()
    findings = []
    for node in ast.walk(tree):
        if not isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
            continue
        for decorator in node.decorator_list:
            route = _match_route_decorator(decorator, router_names)
            if route is None:
                continue
            method, path = route
            authenticated = _has_depends_param(node)
            resolution = resolve_dependencies(node)
            findings.append(
                _evidence(node, file_path, method, path, authenticated, resolution, policy)
            )
    return findings


def _evidence(
    node,
    file_path: str,
    method: str,
    path: str,
    authenticated: bool,
    resolution: Resolution,
    policy: SensitivityPolicy,
) -> dict:
    primary = resolution.primary
    classification = policy.classify(primary.resource, primary.repository is not None)
    dependency_paths = [
        {
            "service": dependency.service,
            "repository": dependency.repository,
            "resource": dependency.resource,
            "sensitivity": policy.classify(
                dependency.resource, dependency.repository is not None
            ).sensitivity,
        }
        for dependency in resolution.paths
    ]
    route_confidence = "HIGH"
    authorization_confidence = "MEDIUM" if authenticated else "HIGH"
    overall = confidence_minimum(route_confidence, authorization_confidence, resolution.confidence)
    return {
        "endpoint": {
            "endpoint": path,
            "method": method,
            "controller": node.name,
            "authentication": authenticated,
            "required_role": None,
            "service": primary.service,
            "repository": primary.repository,
            "resource": primary.resource,
            "sensitivity": classification.sensitivity,
        },
        "source_location": {
            "path": file_path,
            "start_line": node.lineno,
            "end_line": getattr(node, "end_lineno", node.lineno),
        },
        "qualified_controller": f"{file_path}:{node.name}",
        "method_signature": _signature(node),
        "dependency_paths": dependency_paths,
        "sensitivity_evidence": {
            "classification": classification.sensitivity,
            "source": classification.source,
            "matched_rule": classification.matched_rule,
        },
        "extraction_confidence": {
            "route": route_confidence,
            "authorization": authorization_confidence,
            "call_resolution": resolution.confidence,
            "overall": overall,
        },
    }


def _router_variable_names(tree: ast.Module) -> set[str]:
    """Names bound to `FastAPI()`/`APIRouter()`, e.g. `app = FastAPI()`."""
    names = set()
    for node in ast.walk(tree):
        if isinstance(node, ast.Assign) and isinstance(node.value, ast.Call):
            callee = node.value.func
            callee_name = (
                callee.id if isinstance(callee, ast.Name) else getattr(callee, "attr", None)
            )
            if callee_name in {"FastAPI", "APIRouter"}:
                for target in node.targets:
                    if isinstance(target, ast.Name):
                        names.add(target.id)
    # A file that only imports a shared `app`/`router` (no local assignment)
    # still needs to match the conventional names, or nothing is found.
    return names or {"app", "router"}


def _match_route_decorator(decorator: ast.expr, router_names: set[str]) -> tuple[str, str] | None:
    if not isinstance(decorator, ast.Call) or not isinstance(decorator.func, ast.Attribute):
        return None
    attr = decorator.func
    method = attr.attr.lower()
    if method not in _HTTP_METHODS:
        return None
    if not isinstance(attr.value, ast.Name) or attr.value.id not in router_names:
        return None
    if not decorator.args or not isinstance(decorator.args[0], ast.Constant):
        return None
    path = decorator.args[0].value
    if not isinstance(path, str):
        return None
    return method.upper(), path


def _has_depends_param(node) -> bool:
    for default in list(node.args.defaults) + list(node.args.kw_defaults):
        if not isinstance(default, ast.Call):
            continue
        func = default.func
        name = func.id if isinstance(func, ast.Name) else getattr(func, "attr", None)
        if name == "Depends":
            return True
    return False


def _signature(node) -> str:
    args = [a.arg for a in node.args.args]
    return f"{node.name}({', '.join(args)})"
