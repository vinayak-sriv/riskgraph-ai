"""Reproduce a pull_request event locally; static analysis only, no GitHub writes."""

import argparse
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "dev"))
from generate import reports
from platform_session import PlatformSession


def request_for(event, repository_path):
    pr = event["pull_request"]
    old, new = pr["base"]["sha"], pr["head"]["sha"]
    if not all(re.fullmatch("[0-9a-f]{40}", value) for value in (old, new)):
        raise ValueError("Event must contain immutable base/head SHAs")
    return dict(repository_path=str(repository_path.resolve()), old_commit=old, new_commit=new)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("event", type=Path)
    parser.add_argument("repository", type=Path)
    parser.add_argument("--platform", default="http://localhost:8080")
    parser.add_argument("--output", type=Path, default=Path("tmp/github-event"))
    parser.add_argument(
        "--container-repository", help="Repository path as seen by the allowlisted Compose services"
    )
    args = parser.parse_args()
    request = request_for(json.loads(args.event.read_text()), args.repository)
    if args.container_repository:
        request["repository_path"] = args.container_repository
    scan = PlatformSession(args.platform).call("/analyses", request)
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "scan.json").write_text(
        json.dumps(scan, indent=2) + "\n", encoding="utf-8", newline="\n"
    )
    for name, result in reports(scan).items():
        (args.output / name).write_text(
            result if isinstance(result, str) else json.dumps(result, indent=2) + "\n",
            encoding="utf-8",
            newline="\n",
        )


if __name__ == "__main__":
    main()
