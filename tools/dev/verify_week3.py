import json
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


REQUIRED_FILES = [
    "services/platform-api/pom.xml",
    "services/platform-api/Dockerfile",
    "services/platform-api/src/main/java/ai/riskgraph/platform/PlatformApiApplication.java",
    "services/platform-api/src/main/java/ai/riskgraph/platform/api/HealthController.java",
    "services/platform-api/src/main/java/ai/riskgraph/platform/api/ScanController.java",
    "services/java-analyzer/pom.xml",
    "services/java-analyzer/Dockerfile",
    "services/java-analyzer/src/main/java/ai/riskgraph/analyzer/JavaAnalyzerApplication.java",
    "services/java-analyzer/src/main/java/ai/riskgraph/analyzer/api/HealthController.java",
    "services/java-analyzer/src/main/java/ai/riskgraph/analyzer/api/AnalyzeController.java",
    "services/graph-risk-service/app/main.py",
    "services/graph-risk-service/requirements.txt",
    "services/graph-risk-service/Dockerfile",
    "services/ai-validation-service/app/main.py",
    "services/ai-validation-service/requirements.txt",
    "services/ai-validation-service/Dockerfile",
    "apps/dashboard/package.json",
    "apps/dashboard/index.html",
    "apps/dashboard/src/main.tsx",
    "apps/dashboard/src/styles.css",
    "apps/dashboard/Dockerfile",
]


def check_required_files() -> None:
    missing = [path for path in REQUIRED_FILES if not (ROOT / path).exists()]
    if missing:
        print("FAIL missing scaffold files:")
        for path in missing:
            print(f"  {path}")
        raise SystemExit(1)
    print("OK Week 3 scaffold files exist")


def check_pom(path: Path) -> None:
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    root = ET.parse(path).getroot()
    java_version = root.find("m:properties/m:java.version", namespace)
    if java_version is None or java_version.text != "21":
        raise SystemExit(f"FAIL {path.relative_to(ROOT)} should target Java 21")
    print(f"OK {path.relative_to(ROOT)} targets Java 21")


def check_python_syntax() -> None:
    files = [
        ROOT / "services" / "graph-risk-service" / "app" / "main.py",
        ROOT / "services" / "ai-validation-service" / "app" / "main.py",
        ROOT / "tools" / "dev" / "verify_week2.py",
    ]
    subprocess.run([sys.executable, "-m", "py_compile", *map(str, files)], check=True)
    print("OK Python scaffold syntax")


def check_dashboard_package() -> None:
    package_path = ROOT / "apps" / "dashboard" / "package.json"
    package = json.loads(package_path.read_text(encoding="utf-8"))
    for script in ["dev", "build", "preview"]:
        if script not in package.get("scripts", {}):
            raise SystemExit(f"FAIL dashboard package missing {script} script")
    print("OK dashboard package scripts")


def main() -> None:
    check_required_files()
    check_pom(ROOT / "services" / "platform-api" / "pom.xml")
    check_pom(ROOT / "services" / "java-analyzer" / "pom.xml")
    check_python_syntax()
    check_dashboard_package()
    print("Week 3 scaffold verification passed")


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as error:
        print(f"FAIL command exited {error.returncode}: {' '.join(error.cmd)}")
        sys.exit(error.returncode)
