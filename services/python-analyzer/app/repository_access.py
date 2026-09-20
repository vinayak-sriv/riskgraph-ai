"""Repository-root allowlist, mirroring java-analyzer's GitSourceAcquirer.

The analyzer shells out to `git -C <repository_path>`, so an unconstrained
path lets any holder of the analyzer service token read arbitrary files back
through the envelope evidence. The Java analyzer enforces an allowlist; this
is the same check for the Python one.
"""

import os
from pathlib import Path

ALLOWED_ROOTS_ENV = "RISKGRAPH_ALLOWED_REPOSITORY_ROOTS"


def allowed_roots() -> list[Path]:
    raw = os.environ.get(ALLOWED_ROOTS_ENV, "")
    return [Path(entry).resolve() for entry in raw.split(os.pathsep) if entry.strip()]


class RepositoryNotAllowed(Exception):
    """Raised with a stable reason code for the API layer to map to a status."""

    def __init__(self, code: str, message: str) -> None:
        super().__init__(message)
        self.code = code


def validate_repository_path(repository_path: str) -> str:
    """Resolve the path and require it inside a configured allowed root.

    Fails closed: an unset or empty allowlist rejects everything, matching the
    service-token behaviour rather than defaulting to the working directory.
    """
    roots = allowed_roots()
    if not roots:
        raise RepositoryNotAllowed(
            "REPOSITORY_ROOTS_NOT_CONFIGURED",
            f"{ALLOWED_ROOTS_ENV} must be configured",
        )
    try:
        real = Path(repository_path).resolve(strict=True)
    except OSError as error:
        raise RepositoryNotAllowed(
            "REPOSITORY_NOT_FOUND", "Repository path does not exist"
        ) from error
    if not any(real == root or root in real.parents for root in roots):
        raise RepositoryNotAllowed(
            "REPOSITORY_NOT_ALLOWED", "Repository must be inside an allowed repository root"
        )
    return str(real)
