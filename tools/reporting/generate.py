"""Offline GitHub Check / SARIF / summary generation. This never publishes."""

import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath


def stable_fingerprint(repository, endpoint, resource):
    return hashlib.sha256(
        json.dumps(
            ["anonymous-sensitive-path-v1", repository, endpoint, resource],
            separators=(",", ":"),
            ensure_ascii=True,
        ).encode()
    ).hexdigest()


def reports(scan):
    provenance = scan.get("provenance", {})
    repository = provenance.get("repository_identity", "riskgraph-demo")
    findings, annotations = [], []
    for path in scan["graph_delta"]["new_paths"]:
        endpoint_id = path["nodes"][1]
        _, method, route = endpoint_id.split(":", 2)
        fingerprint = stable_fingerprint(repository, endpoint_id, path["target"])
        message = f"New anonymous {method} {route} path reaches {path['target']}. Validate in a local Docker sandbox."
        finding = {
            "ruleId": "RG001",
            "level": "error" if scan["verdict"] == "BLOCK" else "warning",
            "message": {"text": message},
            "partialFingerprints": {"riskgraph/v1": fingerprint},
        }
        for row in scan.get("source_evidence", {}).get("after", []):
            if row["endpoint"]["endpoint"] != route or row["endpoint"]["method"] != method:
                continue
            location = row["source_location"]
            file = location["path"].replace("\\", "/")
            if (
                PurePosixPath(file).is_absolute()
                or ".." in PurePosixPath(file).parts
                or ":" in file
            ):
                continue
            start, end = location["start_line"], location["end_line"]
            if not 1 <= start <= end:
                continue
            finding["locations"] = [
                {
                    "physicalLocation": {
                        "artifactLocation": {"uri": file},
                        "region": {"startLine": start, "endLine": end},
                    }
                }
            ]
            annotations.append(
                {
                    "path": file,
                    "start_line": start,
                    "end_line": end,
                    "annotation_level": "failure" if scan["verdict"] == "BLOCK" else "warning",
                    "message": message[:60000],
                    "title": "New anonymous sensitive path",
                }
            )
            break
        findings.append(finding)
    # Sort/deduplicate by stable identity, independently of traversal and source ordering.
    findings = sorted(
        {f["partialFingerprints"]["riskgraph/v1"]: f for f in findings}.values(),
        key=lambda f: f["partialFingerprints"]["riskgraph/v1"],
    )
    risk = scan["risk_result"]
    verdict = scan.get("final_verdict", scan["verdict"])
    summary = (
        f"RiskGraph: **{verdict}**\n\nRisk {risk['risk_before']} → {risk['risk_after']} "
        f"(delta {risk['risk_delta']:+d}); {len(findings)} new sensitive paths.\n\n"
        f"Preliminary: {scan.get('pre_validation_verdict', scan['verdict'])}. "
        f"Validation: {scan.get('validation_status', 'NOT_RUN')}. "
        f"Extraction confidence: {scan.get('quality', {}).get('confidence', 'UNKNOWN')}.\n\n"
        f"Analysis status: {scan.get('status', 'FIXTURE')}. "
        "AI text is unconfirmed. Incomplete extraction requires review.\n"
    )
    check = {
        "name": "RiskGraph AI",
        "head_sha": provenance.get("new_commit", "0" * 40),
        "status": "completed",
        "conclusion": {"BLOCK": "failure", "REVIEW": "neutral", "ALLOW": "success"}[verdict],
        "output": {
            "title": f"{verdict}: risk delta {risk['risk_delta']:+d}",
            "summary": summary,
            "annotations": sorted(annotations, key=lambda a: (a["path"], a["start_line"]))[:50],
        },
    }
    sarif = {
        "$schema": "https://json.schemastore.org/sarif-2.1.0.json",
        "version": "2.1.0",
        "runs": [
            {
                "tool": {
                    "driver": {
                        "name": "RiskGraph AI",
                        "version": "1.0.0",
                        "rules": [
                            {
                                "id": "RG001",
                                "shortDescription": {"text": "New anonymous sensitive path"},
                            }
                        ],
                    }
                },
                "results": findings,
                "invocations": [{"executionSuccessful": scan.get("status") != "FAILED"}],
            }
        ],
    }
    return {"check.json": check, "results.sarif": sarif, "summary.md": summary}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("scan", type=Path)
    parser.add_argument("--output", type=Path, default=Path("tmp/reporting"))
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    for name, value in reports(json.loads(args.scan.read_text(encoding="utf-8"))).items():
        (args.output / name).write_text(
            value if isinstance(value, str) else json.dumps(value, indent=2) + "\n",
            encoding="utf-8",
            newline="\n",
        )


if __name__ == "__main__":
    main()
