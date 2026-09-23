"""Build a deterministic source archive from a clean, explicit file selection."""

from __future__ import annotations

import argparse
import hashlib
import json
import zipfile
from pathlib import Path, PurePosixPath

EXCLUDED_DIRECTORIES = {
    ".git",
    ".gradle",
    ".idea",
    ".mypy_cache",
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
EXCLUDED_DIRECTORY_PREFIXES = ("pytest-cache-files-", ".chart-data-")
EXCLUDED_ANY_DIRECTORY_PREFIXES = (
    ".test-",
    ".release-test-",
    ".venv-",
    *EXCLUDED_DIRECTORY_PREFIXES,
)


def excluded_path(parts: tuple[str, ...]) -> bool:
    return any(
        part in EXCLUDED_DIRECTORIES or part.startswith(EXCLUDED_ANY_DIRECTORY_PREFIXES)
        for part in parts
    ) or any(parts[: len(prefix)] == prefix for prefix in EXCLUDED_PATH_PREFIXES)


def validated_source_file(root: Path, path: Path) -> Path:
    relative = path.relative_to(root)
    current = root
    for part in relative.parts:
        current = current / part
        if current.is_symlink():
            raise ValueError(f"Release source cannot contain symlinks: {relative.as_posix()}")
    try:
        resolved = path.resolve(strict=True)
    except OSError as error:
        raise ValueError(
            f"Release source file cannot be resolved: {relative.as_posix()}"
        ) from error
    if not resolved.is_relative_to(root) or not resolved.is_file():
        raise ValueError(f"Release source resolves outside the repository: {relative.as_posix()}")
    return resolved


def included_files(root: Path) -> list[Path]:
    root = root.resolve(strict=True)
    files = []
    for path in root.rglob("*"):
        relative = path.relative_to(root)
        if excluded_path(relative.parts):
            continue
        if path.is_symlink():
            raise ValueError(f"Release source cannot contain symlinks: {relative.as_posix()}")
        if not path.is_file() or path.name in EXCLUDED_FILES or path.suffix in EXCLUDED_SUFFIXES:
            continue
        if path.name.startswith(".env.") and path.name != ".env.example":
            continue
        validated_source_file(root, path)
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
            archive.writestr(info, validated_source_file(root, path).read_bytes())


def verify_archive(path: Path) -> dict[str, object]:
    """Fail closed if a produced archive violates the release allowlist rules."""
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if names != sorted(names) or len(names) != len(set(names)):
            raise ValueError("Archive entries must be unique and lexically ordered")
        for info in archive.infolist():
            relative = PurePosixPath(info.filename)
            if (
                relative.is_absolute()
                or ".." in relative.parts
                or "\\" in info.filename
                or info.is_dir()
            ):
                raise ValueError(f"Unsafe archive entry: {info.filename}")
            if info.date_time != ARCHIVE_TIMESTAMP:
                raise ValueError(f"Non-deterministic timestamp: {info.filename}")
            if excluded_path(relative.parts):
                raise ValueError(f"Excluded directory in archive: {info.filename}")
            if relative.name in EXCLUDED_FILES or relative.suffix in EXCLUDED_SUFFIXES:
                raise ValueError(f"Excluded file in archive: {info.filename}")
            if relative.name.startswith(".env.") and relative.name != ".env.example":
                raise ValueError(f"Environment secret candidate in archive: {info.filename}")
        return {"archive": path.name, "entries": len(names), "sha256": digest}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--output", type=Path, default=Path("riskgraph-source.zip"))
    parser.add_argument(
        "--manifest",
        type=Path,
        help="Write verification metadata next to the archive as JSON",
    )
    args = parser.parse_args()
    output = args.output.resolve()
    build_archive(args.root, output)
    metadata = verify_archive(output)
    if args.manifest:
        args.manifest.resolve().write_text(
            json.dumps(metadata, indent=2) + "\n", encoding="utf-8", newline="\n"
        )
    print(json.dumps(metadata, sort_keys=True))


if __name__ == "__main__":
    main()
