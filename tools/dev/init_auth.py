"""Provision a local bootstrap secret once; never print it or change an account."""

import os
import secrets
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PASSWORD_FILE = ROOT / "tmp/local-auth/admin.password"


def initialize():
    PASSWORD_FILE.parent.mkdir(parents=True, exist_ok=True)
    if not PASSWORD_FILE.exists():
        descriptor = os.open(PASSWORD_FILE, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as output:
            output.write(secrets.token_urlsafe(24) + "\n")
    return PASSWORD_FILE


if __name__ == "__main__":
    print("Administrator username: admin. Local password file: " + str(initialize()))
    print("Existing credentials are preserved. Keep this ignored file private.")
