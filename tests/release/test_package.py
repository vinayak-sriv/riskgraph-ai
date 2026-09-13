import hashlib
import importlib.util
import shutil
import uuid
import zipfile
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("release_package", ROOT / "tools/release/package.py")
PACKAGE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PACKAGE)


def test_release_archive_is_clean_and_byte_deterministic():
    work = ROOT / f".release-test-{uuid.uuid4().hex}"
    source = work / "source"
    try:
        for relative, content in {
            "src/app.py": b"print('safe')\n",
            ".env.example": b"TOKEN=replace-me\n",
            ".env": b"SECRET=real\n",
            "tmp/admin.password": b"secret\n",
            ".git/config": b"private\n",
            "apps/ui/node_modules/module.js": b"generated\n",
            "services/api/target/app.jar": b"generated\n",
            "apps/ui/dist/app.js": b"generated\n",
            "coverage/report.json": b"generated\n",
            ".ruff_cache/cache": b"generated\n",
            ".test-tmp/output.json": b"generated\n",
            ".review-logic-pytest/result.json": b"generated\n",
            "pytest-cache-files-abc/cache": b"generated\n",
            "samples/generated/demo/Application.java": b"generated\n",
            "apps/dashboard.zip": b"generated\n",
        }.items():
            path = source / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
        first, second = work / "first.zip", work / "second.zip"
        PACKAGE.build_archive(source, first)
        PACKAGE.build_archive(source, second)

        assert (
            hashlib.sha256(first.read_bytes()).digest()
            == hashlib.sha256(second.read_bytes()).digest()
        )
        with zipfile.ZipFile(first) as archive:
            assert archive.namelist() == [".env.example", "src/app.py"]
            assert all(info.date_time == PACKAGE.ARCHIVE_TIMESTAMP for info in archive.infolist())
        assert PACKAGE.verify_archive(first) == {
            "archive": "first.zip",
            "entries": 2,
            "sha256": hashlib.sha256(first.read_bytes()).hexdigest(),
        }
    finally:
        shutil.rmtree(work, ignore_errors=True)


def test_release_archive_verifier_rejects_secret_and_traversal_entries():
    work = ROOT / f".release-test-{uuid.uuid4().hex}"
    archive_path = work / "unsafe.zip"
    try:
        work.mkdir()
        with zipfile.ZipFile(archive_path, "w") as archive:
            for name in ("../escape.txt", ".env"):
                info = zipfile.ZipInfo(name, PACKAGE.ARCHIVE_TIMESTAMP)
                archive.writestr(info, b"secret")

        with pytest.raises(ValueError, match="Unsafe archive entry"):
            PACKAGE.verify_archive(archive_path)
    finally:
        shutil.rmtree(work, ignore_errors=True)
