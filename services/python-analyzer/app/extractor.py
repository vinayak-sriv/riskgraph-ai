"""FastAPI route/handler/auth-dependency extraction from Python AST.

Track A batch 3 (docs/pending-updates.md): extracts route decorators (HTTP
method + path), the handler function, and whether the handler takes a
FastAPI `Depends(...)` parameter (authentication evidence), all via the
standard `ast` module -- no execution of target source.

# ponytail: two things batch 3's own spec asks for are NOT done here, both
# flagged via extraction_confidence rather than silently guessed:
#   - Router-prefix stitching: `app.include_router(router, prefix="/api")`
#     across files is not resolved, so a route mounted under a prefix is
#     reported with its bare in-file path only. Upgrade path: build a
#     cross-file include_router graph once batch 4's resolver exists.
#   - Call/resource resolution (service/repository/resource paths) is
#     explicitly batch 4. Every finding below reports resource="unresolved"
#     and call_resolution confidence LOW so downstream risk scoring can
#     never mistake this for a confirmed-safe path.
"""

import ast

_HTTP_METHODS = {"get", "post", "put", "patch", "delete"}


def extract_endpoints(source: str, file_path: str) -> list[dict]:
    """Parse FastAPI route handlers out of one Python file's source.

    Returns a list of dicts matching the analysis-envelope "evidence" shape
    (contracts/ir/analysis-envelope.schema.json) apart from dependency_paths
    and sensitivity_evidence, which stay empty/placeholder pending batch 4.
    Returns [] on a syntax error rather than raising -- a diagnostic at the
    call site is the right place to surface that, not a crashed scan.
    """
    try:
        tree = ast.parse(source, filename=file_path)
    except SyntaxError:
        return []

    router_names = _router_variable_names(tree)
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
            findings.append(_evidence(node, file_path, method, path, authenticated))
    return findings


def _evidence(node, file_path: str, method: str, path: str, authenticated: bool) -> dict:
    return {
        "endpoint": {
            "endpoint": path,
            "method": method,
            "controller": node.name,
            "authentication": authenticated,
            "required_role": None,
            "service": None,
            "repository": None,
            "resource": "unresolved",
            "sensitivity": "LOW",
        },
        "source_location": {
            "path": file_path,
            "start_line": node.lineno,
            "end_line": getattr(node, "end_lineno", node.lineno),
        },
        "qualified_controller": f"{file_path}:{node.name}",
        "method_signature": _signature(node),
        "dependency_paths": [],
        "sensitivity_evidence": {
            "classification": "LOW",
            "source": "default",
            "matched_rule": "unresolved-pending-batch-4",
        },
        "extraction_confidence": {
            "route": "HIGH",
            "authorization": "MEDIUM" if authenticated else "HIGH",
            "call_resolution": "LOW",
            "overall": "LOW",
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
