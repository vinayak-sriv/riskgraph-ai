"""`.env.example` is the only documentation of the configuration surface.

Fifteen variables the code read were missing from it, including the ones that
decide whether validation touches the host Docker socket and which
repositories an analyzer may read. This keeps that class of drift mechanical.
"""

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EXAMPLE = ROOT / ".env.example"

# Read from the environment but intentionally not configuration: CI-provided,
# or set by the runtime rather than by an operator.
NOT_OPERATOR_CONFIGURED = {
    "RISKGRAPH_PASSWORD_FILE",  # documented inline as a verification-client option
}

SEARCH_ROOTS = ("services", "apps", "tools", "infrastructure")
SOURCE_SUFFIXES = {".java", ".py", ".yml", ".yaml", ".ts", ".tsx"}
# Only the project's own namespace; GITHUB_*/OLLAMA_* names collide with
# reason-code string constants and CI-provided variables.
PATTERN = re.compile(r"\bRISKGRAPH_[A-Z0-9_]+\b")


SKIP_DIRS = {"node_modules", "target", "dist", "coverage", "__pycache__", ".venv"}


def _source_files(root: Path):
    """Walk with pruning -- rglob descends into node_modules before filtering."""
    stack = [root]
    while stack:
        for entry in stack.pop().iterdir():
            if entry.is_dir():
                if entry.name not in SKIP_DIRS and not entry.name.startswith(".venv"):
                    stack.append(entry)
            elif entry.suffix in SOURCE_SUFFIXES:
                yield entry


def _referenced() -> set[str]:
    names: set[str] = set()
    for root in SEARCH_ROOTS:
        for path in _source_files(ROOT / root):
            names.update(PATTERN.findall(path.read_text(encoding="utf-8", errors="ignore")))
    return names


def _documented() -> set[str]:
    text = EXAMPLE.read_text(encoding="utf-8")
    # Commented-out defaults still count as documentation.
    return set(re.findall(r"^#?\s*([A-Z][A-Z0-9_]+)=", text, flags=re.MULTILINE))


def test_every_riskgraph_variable_the_code_reads_is_documented():
    missing = _referenced() - _documented() - NOT_OPERATOR_CONFIGURED

    assert not missing, "add these to .env.example (with a one-line comment each): " + ", ".join(
        sorted(missing)
    )
