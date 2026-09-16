"""Start the local mentor prototype without changing repository history.

Use --stop to stop only processes launched by this script. Default mode uses
explicit ephemeral platform storage; Compose uses PostgreSQL and Flyway.
"""

import argparse
import json
import os
import secrets
import shutil
import socket
import subprocess
import sys
import urllib.request
from pathlib import Path

import psutil
from init_auth import initialize

ROOT = Path(__file__).resolve().parents[2]
RUNTIME = ROOT / "tmp" / "prototype"


def load_root_env(environment):
    """Load ignored local configuration without printing or overriding shell values."""
    path = ROOT / ".env"
    if not path.is_file():
        return
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key, value = key.strip(), value.strip()
        if value[:1] == value[-1:] and value[:1] in {'"', "'"}:
            value = value[1:-1]
        environment.setdefault(key, value)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--stop", action="store_true")
    args = parser.parse_args()
    RUNTIME.mkdir(parents=True, exist_ok=True)
    state = RUNTIME / "processes.json"

    def owned(item):
        try:
            process = psutil.Process(item["pid"])
            return "started_at" in item and abs(process.create_time() - item["started_at"]) < 0.01
        except psutil.Error:
            return False

    if args.stop:
        if state.exists():
            for item in json.loads(state.read_text()):
                if owned(item):
                    process = psutil.Process(item["pid"])
                    for child in process.children(recursive=True):
                        child.terminate()
                    process.terminate()
            state.unlink()
        return
    env = os.environ.copy()
    load_root_env(env)
    env.setdefault("RISKGRAPH_ANALYZER_SERVICE_TOKEN", secrets.token_urlsafe(32))
    env.setdefault("RISKGRAPH_GRAPH_SERVICE_TOKEN", secrets.token_urlsafe(32))
    env.setdefault("RISKGRAPH_AI_SERVICE_TOKEN", secrets.token_urlsafe(32))
    env.update(
        RISKGRAPH_ALLOWED_REPOSITORY_ROOTS=str(ROOT / "samples" / "generated"),
        SPRING_PROFILES_ACTIVE="local",
        RISKGRAPH_BOOTSTRAP_PASSWORD_FILE=str(initialize()),
        JAVA_ANALYZER_BASE_URL="http://127.0.0.1:8081",
        GRAPH_RISK_BASE_URL="http://127.0.0.1:8082",
        AI_VALIDATION_BASE_URL="http://127.0.0.1:8083",
        RISKGRAPH_ANALYZER_PROCESS_ISOLATION="true",
        RISKGRAPH_ANALYZER_EXECUTABLE_JAR=str(RUNTIME / "java-analyzer.jar"),
        # This launcher binds only to localhost over plain HTTP. A Secure cookie
        # would be accepted at login but never returned by verification clients.
        RISKGRAPH_SECURE_COOKIE="false",
        RISKGRAPH_REQUIRE_GITHUB_CONNECTION="false",
    )
    java = (
        str(Path(env["JAVA_HOME"]) / "bin/java.exe")
        if os.name == "nt" and env.get("JAVA_HOME")
        else "java"
    )
    services = [
        (
            "graph",
            [
                sys.executable,
                "-m",
                "uvicorn",
                "app.main:app",
                "--host",
                "127.0.0.1",
                "--port",
                "8082",
            ],
            ROOT / "services/graph-risk-service",
            8082,
        ),
        (
            "analyzer",
            [java, "-jar", str(RUNTIME / "java-analyzer.jar"), "--server.address=127.0.0.1"],
            ROOT,
            8081,
        ),
        (
            "ai",
            [
                sys.executable,
                "-m",
                "uvicorn",
                "app.main:app",
                "--host",
                "127.0.0.1",
                "--port",
                "8083",
            ],
            ROOT / "services/ai-validation-service",
            8083,
        ),
        (
            "platform",
            [java, "-jar", str(RUNTIME / "platform-api.jar"), "--server.address=127.0.0.1"],
            ROOT,
            8080,
        ),
        (
            "dashboard",
            [
                "node",
                str(ROOT / "apps/dashboard/node_modules/vite/bin/vite.js"),
                "--host",
                "127.0.0.1",
                "--port",
                "5173",
                "--strictPort",
            ],
            ROOT / "apps/dashboard",
            5173,
        ),
    ]
    launched = (
        [item for item in json.loads(state.read_text()) if owned(item)] if state.exists() else []
    )
    for name, command, cwd, port in services:
        # An unhealthy listener may be somebody else's process. Never replace it.
        with socket.socket() as sock:
            sock.settimeout(0.5)
            if sock.connect_ex(("127.0.0.1", port)) == 0:
                print(f"{name}: port {port} is already occupied; preserving existing listener")
                continue
        try:
            urllib.request.urlopen(
                f"http://127.0.0.1:{port}/" + ("" if name == "dashboard" else "health"), timeout=1
            )
            print(f"{name}: already listening on {port}")
            continue
        except Exception:
            pass
        if name in ("analyzer", "platform"):
            artifact = "java-analyzer" if name == "analyzer" else "platform-api"
            shutil.copy2(
                ROOT / f"services/{artifact}/target/{artifact}-0.1.0-SNAPSHOT.jar",
                RUNTIME / f"{artifact}.jar",
            )
        with (RUNTIME / f"{name}.log").open("ab") as log:
            process = subprocess.Popen(
                command,
                cwd=cwd,
                env=env,
                stdout=log,
                stderr=log,
                creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
                start_new_session=os.name != "nt",
            )
        launched.append(
            dict(name=name, pid=process.pid, started_at=psutil.Process(process.pid).create_time())
        )
        print(f"{name}: started pid {process.pid}, port {port}")
    state.write_text(json.dumps(launched, indent=2) + "\n", encoding="utf-8", newline="\n")
    print("Dashboard: http://localhost:5173 · logs: tmp/prototype")


if __name__ == "__main__":
    main()
