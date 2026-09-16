from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]


def test_postgres_image_preserves_entrypoint_privilege_drop_for_fresh_volumes():
    dockerfile = (ROOT / "infrastructure/postgres/Dockerfile").read_text(encoding="utf-8")
    compose = (ROOT / "infrastructure/docker-compose.yml").read_text(encoding="utf-8")
    postgres_service = compose.split("  platform-api:", 1)[0]
    instructions = [
        line.strip()
        for line in dockerfile.splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    ]

    assert "COPY --from=gosu-builder /go/bin/gosu /usr/local/bin/gosu" in dockerfile
    assert all(not instruction.startswith("USER ") for instruction in instructions)
    assert "\n    user:" not in postgres_service


def test_postgres_root_bootstrap_has_one_path_scoped_trivy_exception():
    ignore = yaml.safe_load((ROOT / ".trivyignore.yaml").read_text(encoding="utf-8"))
    workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")

    assert ignore == {
        "misconfigurations": [
            {
                "id": "AVD-DS-0002",
                "paths": ["infrastructure/postgres/Dockerfile"],
                "statement": (
                    "The official PostgreSQL entrypoint must start as root to initialize "
                    "fresh volume ownership, then immediately drops to the postgres user "
                    "through the rebuilt gosu binary."
                ),
            }
        ]
    }
    assert "--ignorefile .trivyignore.yaml" in workflow


def test_runtime_cleanup_check_targets_the_isolated_validation_daemon():
    verifier = (ROOT / "tools/dev/verify_runtime.py").read_text(encoding="utf-8")

    assert 'str(ROOT / "infrastructure/docker-compose.validation.yml")' in verifier
    assert 'compose + ["exec", "-T", "validation-docker", "docker", *command]' in verifier
