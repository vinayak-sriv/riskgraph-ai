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

FunctionDef = ast.FunctionDef | ast.AsyncFunctionDef

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
            authenticated, authorization_confidence = _authorization(node)
            resolution = resolve_dependencies(node)
            findings.append(
                _evidence(
                    node,
                    file_path,
                    method,
                    path,
                    authenticated,
                    authorization_confidence,
                    resolution,
                    policy,
                )
            )
    return findings


def _evidence(
    node: FunctionDef,
    file_path: str,
    method: str,
    path: str,
    authenticated: bool,
    authorization_confidence: str,
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
    # `@router.get("")` and `@router.post("items")` are legal under an
    # include_router prefix. The shared IR requires an absolute route, and an
    # unnormalized one fails validation downstream, failing the whole scan with
    # a 502 over a single unconventional route.
    if not path.startswith("/"):
        path = "/" + path
    return method.upper(), path


# Substrings that mark a dependency as an authentication/authorization check.
# Everything else (get_db, get_settings, pagination, ...) is plain injection.
_AUTH_DEPENDENCY_HINTS = (
    "auth",
    "current_user",
    "currentuser",
    "token",
    "identity",
    "principal",
    "permission",
    "scope",
    "security",
    "login",
    "jwt",
    "oauth",
    "bearer",
    "require",
)


def _callee_name(func: ast.expr) -> str:
    return func.id if isinstance(func, ast.Name) else getattr(func, "attr", "") or ""


def _depends_calls(node: FunctionDef) -> list[ast.Call]:
    """Every `Depends(...)` on the handler, from defaults and from annotations.

    `user: Annotated[User, Depends(get_current_user)]` is the modern form and
    is not a parameter default, so inspecting defaults alone misses it.
    """
    arguments = node.args
    calls = [
        default
        for default in [*arguments.defaults, *arguments.kw_defaults]
        if isinstance(default, ast.Call) and _callee_name(default.func) == "Depends"
    ]
    for argument in [*arguments.posonlyargs, *arguments.args, *arguments.kwonlyargs]:
        if argument.annotation is None:
            continue
        calls.extend(
            sub
            for sub in ast.walk(argument.annotation)
            if isinstance(sub, ast.Call) and _callee_name(sub.func) == "Depends"
        )
    return calls


def _dependency_name(call: ast.Call) -> str:
    """`Depends(get_db)` -> "get_db"; `Depends(HTTPBearer())` -> "HTTPBearer"."""
    if not call.args:
        return ""
    argument = call.args[0]
    if isinstance(argument, ast.Call):
        return _callee_name(argument.func)
    if isinstance(argument, (ast.Name, ast.Attribute)):
        return _callee_name(argument)
    return ""


def _authorization(node: FunctionDef) -> tuple[bool, str]:
    """Return (authenticated, authorization_confidence).

    A bare `Depends(...)` is not evidence of authentication: `Depends(get_db)`
    is the most common FastAPI idiom and only injects a database session.
    Treating it as auth silently suppresses the anonymous-reachability check.
    An unrecognised dependency is therefore reported unauthenticated at LOW
    confidence, so the verdict escalates rather than trusting the guess.
    """
    names = [_dependency_name(call).lower() for call in _depends_calls(node)]
    if not names:
        return False, "HIGH"
    if any(hint in name for name in names for hint in _AUTH_DEPENDENCY_HINTS):
        return True, "MEDIUM"
    return False, "LOW"


def _signature(node: FunctionDef) -> str:
    args = [a.arg for a in node.args.args]
    return f"{node.name}({', '.join(args)})"
