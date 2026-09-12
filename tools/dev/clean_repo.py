"""Remove reproducible build, dependency, cache, and local runtime artifacts.

This intentionally preserves source code, datasets, documentation, Git metadata,
and local environment files. Use the release packager when you need a shareable
archive that also excludes Git metadata and secrets.
"""

from __future__ import annotations

import argparse
import os
import shutil
import stat
from pathlib import Path

FIXED_PATHS = (
    ".coverage",
    ".pytest_cache",
    ".ruff_cache",
    ".test-tmp",
    ".review-logic-pytest",
    "apps/dashboard/node_modules",
    "apps/dashboard/dist",
    "apps/dashboard/tsconfig.tsbuildinfo",
    "apps/dashboard.zip",
    "riskgraph-dataset-package.zip",
    "samples/generated",
    "samples/sandbox/target",
    "services/java-analyzer/target",
    "services/platform-api/target",
    "tmp",
)
GLOB_PATTERNS = (
    "**/__pycache__",
    "pytest-cache-files-*",
)


def bytes_used(path: Path) -> int:
    if path.is_file() or path.is_symlink():
        return path.stat().st_size
    return sum(item.stat().st_size for item in path.rglob("*") if item.is_file())


def remove_readonly(function, path: str, error: OSError) -> None:
    """Retry deletion of read-only generated files, notably nested Git packs on Windows."""
    try:
        os.chmod(path, stat.S_IWRITE)
        function(path)
    except OSError:
        raise error from None


def cleanup(root: Path, *, dry_run: bool = False) -> tuple[int, list[Path]]:
    root = root.resolve()
    candidates: set[Path] = set()
    for relative in FIXED_PATHS:
        path = root / relative
        if path.exists() or path.is_symlink():
            candidates.add(path)
    for pattern in GLOB_PATTERNS:
        candidates.update(path for path in root.glob(pattern) if path.exists())

    # If a parent directory is already scheduled, do not process its children twice.
    ordered = sorted(candidates, key=lambda path: (len(path.parts), path.as_posix()))
    selected: list[Path] = []
    for path in ordered:
        if any(parent in selected for parent in path.parents):
            continue
        selected.append(path)

    freed = sum(bytes_used(path) for path in selected)
    if not dry_run:
        for path in selected:
            if path.is_dir() and not path.is_symlink():
                shutil.rmtree(path, onexc=remove_readonly)
            else:
                path.unlink(missing_ok=True)
    return freed, selected


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    freed, paths = cleanup(args.root, dry_run=args.dry_run)
    verb = "Would remove" if args.dry_run else "Removed"
    for path in paths:
        print(f"{verb}: {path.relative_to(args.root.resolve())}")
    print(f"{verb} {len(paths)} paths, freeing {freed / (1024 * 1024):.1f} MiB.")


if __name__ == "__main__":
    main()
