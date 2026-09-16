"""Create isolated, deterministic synthetic Git fixtures; never touch project Git history.

Existing directories are reused only when their saved manifest matches this version.
There is no recursive deletion and no execution of repository build scripts.
"""

import json
import os
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "samples/generated/mvp-v2"
SOURCE = "src/main/java/demo/"
FILES = {
    SOURCE + "Application.java": """package demo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
@SpringBootApplication @EnableMethodSecurity
public class Application {
    public static void main(String[] args) { SpringApplication.run(Application.class,args); }
}
""",
    SOURCE + "ExportService.java": """package demo;
import org.springframework.stereotype.Service;
@Service class ExportService {
    private final PaymentRepository repository;
    ExportService(PaymentRepository repository) { this.repository=repository; }
    Object export() { return repository.findAll(); }
}
""",
    SOURCE + "PaymentRepository.java": """package demo;
import org.springframework.stereotype.Repository;
import java.util.Map;
@Repository class PaymentRepository {
    Object findAll() { return Map.of("customer_export","sandbox-only"); }
}
""",
    SOURCE + "CatalogService.java": """package demo;
import org.springframework.stereotype.Service;
@Service class CatalogService {
    private final CatalogRepository repository;
    CatalogService(CatalogRepository repository) { this.repository=repository; }
    Object export() { return repository.findAll(); }
}
""",
    SOURCE + "CatalogRepository.java": """package demo;
import org.springframework.stereotype.Repository;
import java.util.Map;
@Repository class CatalogRepository {
    Object findAll() { return Map.of("catalog","public"); }
}
""",
}
CONTROLLER = """package demo;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController class ExportController {
    private final ExportService service;
    ExportController(ExportService service) { this.service=service; }
    @GetMapping("/admin/export")
    @PreAuthorize("hasRole('ADMIN')")
    Object export() { return service.export(); }
}
"""


def git(repo, *args, stdin=None):
    env = {
        **os.environ,
        "GIT_AUTHOR_NAME": "RiskGraph Fixture",
        "GIT_AUTHOR_EMAIL": "fixture@riskgraph.invalid",
        "GIT_COMMITTER_NAME": "RiskGraph Fixture",
        "GIT_COMMITTER_EMAIL": "fixture@riskgraph.invalid",
        "GIT_AUTHOR_DATE": "2026-01-01T00:00:00Z",
        "GIT_COMMITTER_DATE": "2026-01-01T00:00:00Z",
    }
    return subprocess.run(
        ["git", "-c", "core.hooksPath=/dev/null", *args],
        cwd=repo,
        env=env,
        input=stdin,
        capture_output=True,
        text=True,
        check=True,
        timeout=10,
    ).stdout.strip()


def commit_fixture(repo, files, parent=None):
    for name, text in files.items():
        path = repo / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8", newline="\n")
    git(repo, "add", "--all")
    tree = git(repo, "write-tree")
    # Keep the commit object byte-identical across operating systems. Passing the
    # message on stdin in text mode translated LF to CRLF on Windows, so the same
    # tree and metadata produced a different commit identity than Linux CI.
    sha = git(
        repo,
        "commit-tree",
        tree,
        *(["-p", parent] if parent else []),
        "-m",
        "Synthetic RiskGraph test fixture",
    )
    git(repo, "update-ref", "refs/heads/main", sha)
    git(repo, "symbolic-ref", "HEAD", "refs/heads/main")
    return sha


def create():
    manifest_path = OUTPUT / "manifest.json"
    if OUTPUT.exists():
        if not manifest_path.exists():
            raise SystemExit("Refusing to overwrite existing fixture directory")
        result = json.loads(manifest_path.read_text())
        write_compose_manifest(result)
        return result
    OUTPUT.mkdir(parents=True)
    public = CONTROLLER.replace("    @PreAuthorize(\"hasRole('ADMIN')\")\n", "")
    no_endpoint = CONTROLLER.replace('    @GetMapping("/admin/export")\n', "")
    scenarios = {
        "authorization-removal": (CONTROLLER, public),
        "safe-change": (CONTROLLER, CONTROLLER.replace("service.export();", "service.export( );")),
        "new-public-sensitive-endpoint": (no_endpoint, public),
        "sensitive-resource-exposure": (public.replace("ExportService", "CatalogService"), public),
    }
    result = {"version": "1.1.0", "source_type": "SYNTHETIC_AI_ASSISTED", "scenarios": {}}
    for name, (before, after) in scenarios.items():
        repo = OUTPUT / name
        repo.mkdir()
        git(repo, "init", "--quiet")
        old = commit_fixture(repo, {**FILES, SOURCE + "ExportController.java": before})
        new = commit_fixture(repo, {**FILES, SOURCE + "ExportController.java": after}, old)
        result["scenarios"][name] = dict(
            repository_path=str(repo.resolve()), old_commit=old, new_commit=new
        )
    manifest_path.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8", newline="\n")
    write_compose_manifest(result)
    return result


def write_compose_manifest(result):
    manifest = json.loads(json.dumps(result))
    for name, pair in manifest["scenarios"].items():
        pair["repository_path"] = "/analysis-repositories/mvp-v2/" + name
    (OUTPUT / "manifest.compose.json").write_text(
        json.dumps(manifest, indent=2) + "\n", encoding="utf-8", newline="\n"
    )


if __name__ == "__main__":
    print(json.dumps(create(), indent=2))
