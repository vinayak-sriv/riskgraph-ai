"""Package local release evidence without credentials or mutable runtime state."""

from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
import zipfile
from datetime import UTC, datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ARCHIVE_TIMESTAMP = (1980, 1, 1, 0, 0, 0)
EVIDENCE_PATTERNS = (
    "tmp/evidence-bundle/*.json",
    "tmp/external-fastapi-evaluation/*.json",
    "tmp/external-evaluation/*.json",
    "tmp/release-checks/*.json",
    "tmp/release-checks/*.log",
    "tmp/python-coverage.json",
    "tmp/corpus-evaluation.json",
    "dist/riskgraph-demo.webm",
    "dist/riskgraph-demo.manifest.json",
)


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def collect(source_archive: Path, source_manifest: Path) -> list[Path]:
    files = {source_archive.resolve(), source_manifest.resolve()}
    for pattern in EVIDENCE_PATTERNS:
        files.update(path.resolve() for path in ROOT.glob(pattern) if path.is_file())
    missing = [path for path in files if not path.is_file()]
    if missing:
        raise FileNotFoundError(f"Missing release evidence: {missing}")
    return sorted(files, key=lambda path: path.as_posix())


def archive_name(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def build(output: Path, source_archive: Path, source_manifest: Path) -> dict[str, object]:
    files = collect(source_archive, source_manifest)
    source_commit = subprocess.run(
        ["git", "rev-parse", "HEAD"], cwd=ROOT, check=True, capture_output=True, text=True
    ).stdout.strip()
    dirty = bool(
        subprocess.run(
            ["git", "status", "--porcelain"],
            cwd=ROOT,
            check=True,
            capture_output=True,
            text=True,
        ).stdout.strip()
    )
    manifest = {
        "schema_version": "1.0.0",
        "generated_at": datetime.now(UTC).isoformat(),
        "source_commit": source_commit,
        "working_tree_dirty": dirty,
        "release_tag": None,
        "files": [
            {"path": archive_name(path), "bytes": path.stat().st_size, "sha256": digest(path)}
            for path in files
        ],
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as bundle:
        for path in files:
            info = zipfile.ZipInfo(archive_name(path), ARCHIVE_TIMESTAMP)
            info.compress_type = zipfile.ZIP_DEFLATED
            bundle.writestr(info, path.read_bytes())
        info = zipfile.ZipInfo("MANIFEST.json", ARCHIVE_TIMESTAMP)
        info.compress_type = zipfile.ZIP_DEFLATED
        bundle.writestr(info, json.dumps(manifest, indent=2).encode() + b"\n")
    return {
        "archive": output.name,
        "entries": len(files) + 1,
        "sha256": digest(output),
        "working_tree_dirty": dirty,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--source-archive", type=Path, required=True)
    parser.add_argument("--source-manifest", type=Path, required=True)
    args = parser.parse_args()
    result = build(
        args.output.resolve(), args.source_archive.resolve(), args.source_manifest.resolve()
    )
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
