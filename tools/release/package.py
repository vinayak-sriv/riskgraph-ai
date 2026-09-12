"""Build a deterministic source archive from a clean, explicit file selection."""

from __future__ import annotations

import argparse
import zipfile
from pathlib import Path

EXCLUDED_DIRECTORIES = {
    ".git",
    ".gradle",
    ".idea",
    ".pytest_cache",
    ".review-logic-pytest",
    ".ruff_cache",
    ".test-tmp",
    ".venv",
    ".vscode",
    "__pycache__",
    "build",
    "coverage",
    "dist",
    "logs",
    "node_modules",
    "target",
    "tmp",
    "venv",
}
EXCLUDED_FILES = {".coverage", ".env", "Thumbs.db", ".DS_Store"}
EXCLUDED_SUFFIXES = {".class", ".log", ".pid", ".pyc", ".pyo", ".tsbuildinfo", ".zip"}
ARCHIVE_TIMESTAMP = (1980, 1, 1, 0, 0, 0)
EXCLUDED_PATH_PREFIXES = {
    ("samples", "generated"),
}
EXCLUDED_DIRECTORY_PREFIXES = ("pytest-cache-files-",)


def included_files(root: Path) -> list[Path]:
    files = []
    for path in root.rglob("*"):
        relative = path.relative_to(root)
        if any(
            part in EXCLUDED_DIRECTORIES
            or part.startswith((".test-", ".release-test-", *EXCLUDED_DIRECTORY_PREFIXES))
            for part in relative.parts
        ):
            continue
        if any(relative.parts[: len(prefix)] == prefix for prefix in EXCLUDED_PATH_PREFIXES):
            continue
        if not path.is_file() or path.name in EXCLUDED_FILES or path.suffix in EXCLUDED_SUFFIXES:
            continue
        if path.name.startswith(".env.") and path.name != ".env.example":
            continue
        files.append(path)
    return sorted(files, key=lambda item: item.relative_to(root).as_posix())


def build_archive(root: Path, output: Path) -> None:
    root = root.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in included_files(root):
            relative = path.relative_to(root).as_posix()
            info = zipfile.ZipInfo(relative, ARCHIVE_TIMESTAMP)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, path.read_bytes())


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--output", type=Path, default=Path("riskgraph-source.zip"))
    args = parser.parse_args()
    build_archive(args.root, args.output.resolve())


if __name__ == "__main__":
    main()
