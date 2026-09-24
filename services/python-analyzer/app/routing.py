"""Static FastAPI router mount resolution across repository files.

Only literal ``prefix=`` values and explicit imports are followed. Dynamic
router factories remain unsupported and therefore keep their local path.
"""

from __future__ import annotations

import ast
from collections import defaultdict, deque
from dataclasses import dataclass
from pathlib import PurePosixPath


@dataclass(frozen=True)
class RouterKey:
    path: str
    name: str


def resolve_router_prefixes(
    sources: dict[str, str], parsed_trees: dict[str, ast.Module] | None = None
) -> dict[str, dict[str, list[str]]]:
    trees: dict[str, ast.Module] = {} if parsed_trees is None else parsed_trees
    modules: dict[str, str] = {}
    if parsed_trees is None:
        for path, source in sources.items():
            try:
                trees[path] = ast.parse(source, filename=path)
            except SyntaxError:
                continue
    for path in trees:
        modules[_module_name(path)] = path

    routers: dict[RouterKey, tuple[str, bool]] = {}
    imports: dict[str, dict[str, tuple[str, str | None]]] = {}
    for path, tree in trees.items():
        imports[path] = _imports(path, tree, modules)
        for node in ast.walk(tree):
            if not isinstance(node, ast.Assign) or not isinstance(node.value, ast.Call):
                continue
            kind = _callee_name(node.value.func)
            if kind not in {"FastAPI", "APIRouter"}:
                continue
            prefix = _literal_keyword(node.value, "prefix") or ""
            for target in node.targets:
                if isinstance(target, ast.Name):
                    routers[RouterKey(path, target.id)] = (prefix, kind == "FastAPI")

    edges: dict[RouterKey, list[tuple[RouterKey, str]]] = defaultdict(list)
    for path, tree in trees.items():
        for node in ast.walk(tree):
            if not isinstance(node, ast.Call) or not isinstance(node.func, ast.Attribute):
                continue
            if node.func.attr != "include_router" or not node.args:
                continue
            parent = _router_reference(node.func.value, path, imports, modules)
            child = _router_reference(node.args[0], path, imports, modules)
            if parent in routers and child in routers:
                edges[parent].append((child, _literal_keyword(node, "prefix") or ""))

    resolved: dict[RouterKey, set[str]] = defaultdict(set)
    queue: deque[RouterKey] = deque()
    for key, (prefix, is_app) in routers.items():
        if is_app:
            resolved[key].add(_normalise(prefix))
            queue.append(key)

    while queue:
        parent = queue.popleft()
        for child, mount_prefix in edges[parent]:
            child_prefix = routers[child][0]
            for parent_prefix in resolved[parent]:
                full = _join(parent_prefix, mount_prefix, child_prefix)
                if full not in resolved[child]:
                    resolved[child].add(full)
                    queue.append(child)

    result: dict[str, dict[str, list[str]]] = defaultdict(dict)
    for key, (local_prefix, _) in routers.items():
        prefixes = resolved[key] or {_normalise(local_prefix)}
        result[key.path][key.name] = sorted(prefixes)
    return dict(result)


def _imports(
    path: str, tree: ast.Module, modules: dict[str, str]
) -> dict[str, tuple[str, str | None]]:
    aliases: dict[str, tuple[str, str | None]] = {}
    current = _module_name(path)
    package = current if path.endswith("/__init__.py") else current.rpartition(".")[0]
    for node in tree.body:
        if isinstance(node, ast.Import):
            for alias in node.names:
                target = modules.get(alias.name)
                if target:
                    aliases[alias.asname or alias.name.split(".")[0]] = (target, None)
        elif isinstance(node, ast.ImportFrom):
            base_parts: list[str] = []
            if node.level:
                base_parts = package.split(".") if package else []
                base_parts = base_parts[: max(0, len(base_parts) - node.level + 1)]
            base = ".".join([*base_parts, *(node.module or "").split(".")]).strip(".")
            for alias in node.names:
                if alias.name == "*":
                    continue
                child_module = f"{base}.{alias.name}".strip(".")
                if child_module in modules:
                    aliases[alias.asname or alias.name] = (modules[child_module], None)
                elif base in modules:
                    aliases[alias.asname or alias.name] = (modules[base], alias.name)
    return aliases


def _router_reference(
    expression: ast.expr,
    current_path: str,
    imports: dict[str, dict[str, tuple[str, str | None]]],
    modules: dict[str, str],
) -> RouterKey | None:
    if isinstance(expression, ast.Name):
        imported = imports.get(current_path, {}).get(expression.id)
        if imported and imported[1] is not None:
            return RouterKey(imported[0], imported[1])
        return RouterKey(current_path, expression.id)
    if isinstance(expression, ast.Attribute) and isinstance(expression.value, ast.Name):
        imported = imports.get(current_path, {}).get(expression.value.id)
        if imported and imported[1] is None:
            return RouterKey(imported[0], expression.attr)
    return None


def _module_name(path: str) -> str:
    pure = PurePosixPath(path.replace("\\", "/"))
    parts = list(pure.with_suffix("").parts)
    if parts and parts[-1] == "__init__":
        parts.pop()
    return ".".join(parts)


def _callee_name(expression: ast.expr) -> str:
    if isinstance(expression, ast.Name):
        return expression.id
    if isinstance(expression, ast.Attribute):
        return expression.attr
    return ""


def _literal_keyword(call: ast.Call, name: str) -> str | None:
    for keyword in call.keywords:
        if keyword.arg == name and isinstance(keyword.value, ast.Constant):
            return keyword.value.value if isinstance(keyword.value.value, str) else None
    return None


def _normalise(value: str) -> str:
    stripped = value.strip("/")
    return f"/{stripped}" if stripped else ""


def _join(*parts: str) -> str:
    values = [part.strip("/") for part in parts if part.strip("/")]
    return "/" + "/".join(values) if values else ""
