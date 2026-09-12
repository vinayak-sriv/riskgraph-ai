import ast
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def test_all_python_text_generators_force_lf_newlines():
    violations = []
    generators = list((ROOT / "tools").rglob("*.py"))
    generators.extend((ROOT / "datasets" / "risk-corpus" / "tools").rglob("*.py"))
    for path in generators:
        tree = ast.parse(path.read_text(encoding="utf-8"))
        for node in ast.walk(tree):
            if not isinstance(node, ast.Call) or not isinstance(node.func, ast.Attribute):
                continue
            if node.func.attr == "write_text":
                keyword = next((item for item in node.keywords if item.arg == "newline"), None)
                if (
                    keyword is None
                    or not isinstance(keyword.value, ast.Constant)
                    or keyword.value.value != "\n"
                ):
                    violations.append(f"{path.relative_to(ROOT)}:{node.lineno}")
    assert violations == []
