"""FastAPI route/handler/auth-dependency extraction from Python AST.

Track A batch 3 extracts route decorators (HTTP method + path), the handler
function, and FastAPI dependency evidence from defaults, `Annotated` aliases,
router configuration, and route decorators. Batch 4 adds handler -> service/repository ->
resource resolution via resolver.py (see its module docstring for the exact
heuristic scope) and sensitivity classification via sensitivity_policy.py.

Repository-wide callers may provide resolved mount prefixes for each router.
The extractor also handles prefixes and dependency lists declared directly on
an ``APIRouter`` or route decorator.
"""

import ast
from functools import lru_cache
from typing import cast

from .resolver import Resolution, confidence_minimum, resolve_dependencies
from .sensitivity_policy import SensitivityPolicy

FunctionDef = ast.FunctionDef | ast.AsyncFunctionDef

_HTTP_METHODS = {"get", "post", "put", "patch", "delete"}


@lru_cache(maxsize=1)
def _get_policy() -> SensitivityPolicy:
    return SensitivityPolicy()


def extract_endpoints(
    source: str,
    file_path: str,
    router_prefixes: dict[str, list[str]] | None = None,
    function_catalog: dict[str, list[FunctionDef]] | None = None,
    dependency_aliases: dict[str, list[ast.Call] | None] | None = None,
    parsed_tree: ast.Module | None = None,
) -> list[dict]:
    tree = parsed_tree
    if tree is None:
        try:
            tree = ast.parse(source, filename=file_path)
        except SyntaxError:
            return []

    router_configs = _router_configs(tree)
    policy = _get_policy()
    findings = []
    for node in ast.walk(tree):
        if not isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
            continue
        for decorator in node.decorator_list:
            route = _match_route_decorator(decorator, set(router_configs))
            if route is None:
                continue
            route_decorator = cast(ast.Call, decorator)
            method, path, router_name = route
            config = router_configs[router_name]
            authenticated, authorization_confidence = _authorization(
                node, route_decorator, config.dependencies, dependency_aliases or {}
            )
            resolution = resolve_dependencies(node, function_catalog)
            prefixes = (router_prefixes or {}).get(router_name, [config.prefix])
            for prefix in prefixes:
                findings.append(
                    _evidence(
                        node,
                        file_path,
                        method,
                        _join_route(prefix, path),
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


class _RouterConfig:
    def __init__(self, prefix: str = "", dependencies: list[ast.Call] | None = None) -> None:
        self.prefix = prefix
        self.dependencies = dependencies or []


def _router_configs(tree: ast.Module) -> dict[str, _RouterConfig]:
    """Names and static configuration bound to FastAPI/APIRouter objects."""
    configs: dict[str, _RouterConfig] = {}
    for node in ast.walk(tree):
        if isinstance(node, ast.Assign) and isinstance(node.value, ast.Call):
            callee = node.value.func
            callee_name = (
                callee.id if isinstance(callee, ast.Name) else getattr(callee, "attr", None)
            )
            if callee_name in {"FastAPI", "APIRouter"}:
                prefix = _string_keyword(node.value, "prefix") or ""
                dependencies = _keyword_depends_calls(node.value, "dependencies")
                for target in node.targets:
                    if isinstance(target, ast.Name):
                        configs[target.id] = _RouterConfig(prefix, dependencies)
    # A file that only imports a shared `app`/`router` (no local assignment)
    # still needs to match the conventional names, or nothing is found.
    return configs or {"app": _RouterConfig(), "router": _RouterConfig()}


def _match_route_decorator(
    decorator: ast.expr, router_names: set[str]
) -> tuple[str, str, str] | None:
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
    return method.upper(), path, attr.value.id


def _join_route(prefix: str, path: str) -> str:
    normalized_prefix = "/" + prefix.strip("/") if prefix.strip("/") else ""
    if path == "/":
        return normalized_prefix + "/" if normalized_prefix else "/"
    return normalized_prefix + "/" + path.lstrip("/")


def _string_keyword(call: ast.Call, name: str) -> str | None:
    for keyword in call.keywords:
        if keyword.arg == name and isinstance(keyword.value, ast.Constant):
            return keyword.value.value if isinstance(keyword.value.value, str) else None
    return None


def _keyword_depends_calls(call: ast.Call, name: str) -> list[ast.Call]:
    for keyword in call.keywords:
        if keyword.arg != name:
            continue
        return [
            child
            for child in ast.walk(keyword.value)
            if isinstance(child, ast.Call) and _callee_name(child.func) in {"Depends", "Security"}
        ]
    return []


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
    "active_user",
    "superuser",
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


def _authorization(
    node: FunctionDef,
    route_decorator: ast.Call,
    router_dependencies: list[ast.Call],
    dependency_aliases: dict[str, list[ast.Call] | None],
) -> tuple[bool, str]:
    """Return (authenticated, authorization_confidence).

    A bare `Depends(...)` is not evidence of authentication: `Depends(get_db)`
    is the most common FastAPI idiom and only injects a database session.
    Treating it as auth silently suppresses the anonymous-reachability check.
    An unrecognised dependency is therefore reported unauthenticated at LOW
    confidence, so the verdict escalates rather than trusting the guess.
    """
    calls = [
        *_depends_calls(node),
        *_keyword_depends_calls(route_decorator, "dependencies"),
        *router_dependencies,
    ]
    ambiguous_alias = False
    for argument in [*node.args.posonlyargs, *node.args.args, *node.args.kwonlyargs]:
        if isinstance(argument.annotation, ast.Name):
            alias = dependency_aliases.get(argument.annotation.id, [])
            if alias is None:
                ambiguous_alias = True
            else:
                calls.extend(alias)
    names = [_dependency_name(call).lower() for call in calls]
    if not names:
        return False, "LOW" if ambiguous_alias else "HIGH"
    if any(hint in name for name in names for hint in _AUTH_DEPENDENCY_HINTS):
        return True, "MEDIUM"
    return False, "LOW"


def build_dependency_alias_catalog(
    sources: dict[str, str],
    parsed_trees: dict[str, ast.Module] | None = None,
) -> dict[str, list[ast.Call] | None]:
    """Collect named ``Annotated[..., Depends(...)]`` aliases across files."""
    aliases: dict[str, list[ast.Call] | None] = {}
    trees: dict[str, ast.Module] = {} if parsed_trees is None else parsed_trees
    if parsed_trees is None:
        for path, source in sources.items():
            try:
                trees[path] = ast.parse(source, filename=path)
            except SyntaxError:
                continue
    for tree in trees.values():
        for node in tree.body:
            name: str | None = None
            value: ast.expr | None = None
            if (
                isinstance(node, ast.Assign)
                and len(node.targets) == 1
                and isinstance(node.targets[0], ast.Name)
            ):
                name, value = node.targets[0].id, node.value
            elif isinstance(node, ast.AnnAssign) and isinstance(node.target, ast.Name):
                name, value = node.target.id, node.value
            if name is None or value is None:
                continue
            calls = [
                child
                for child in ast.walk(value)
                if isinstance(child, ast.Call)
                and _callee_name(child.func) in {"Depends", "Security"}
            ]
            if calls:
                aliases[name] = calls if name not in aliases else None
    return aliases


def _signature(node: FunctionDef) -> str:
    args = [a.arg for a in node.args.args]
    return f"{node.name}({', '.join(args)})"
