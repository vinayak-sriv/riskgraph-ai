import importlib.util
from pathlib import Path

import pytest
from pydantic import ValidationError

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location(
    "corpus_tools", ROOT / "datasets/risk-corpus/tools/corpus.py"
)
corpus = importlib.util.module_from_spec(spec)
spec.loader.exec_module(corpus)


def test_integrity_arithmetic_splits_and_schema():
    records = corpus.validate()
    assert len(records) == 100
    assert sum(r["safe_negative"] for r in records) == 50


def test_regeneration_is_byte_deterministic():
    before = (corpus.CORPUS / "manifest.json").read_bytes()
    corpus.generate()
    assert (corpus.CORPUS / "manifest.json").read_bytes() == before


def test_pipeline_matches_provisional_oracle():
    result = corpus.evaluate(corpus.validate())
    assert result["graph_path_exact_match"] == 1
    assert result["verdict_accuracy"] == 1
    assert (result["TP"], result["FP"], result["TN"], result["FN"]) == (30, 0, 70, 0)


def test_metrics_are_not_constant_success():
    result = corpus.binary_metrics([True, True, False, False], [True, False, True, False])
    assert result["precision"] == result["recall"] == result["F1"] == 0.5
    row = dict(
        method="GET", endpoint="/x", authentication=True, required_role="ADMIN", sensitivity="HIGH"
    )
    result = corpus.extraction_metrics(
        [row], [{**row, "authentication": False, "sensitivity": "LOW"}]
    )
    assert result == dict(
        endpoint_extraction_accuracy=1, authorization_extraction_accuracy=0, sensitivity_accuracy=0
    )


def test_strict_labels_and_non_docker_targets_rejected():
    record = corpus.validate()[0]
    with pytest.raises(ValidationError):
        corpus.Record.model_validate({**record, "label_status": "HUMAN_VALIDATED"})
    with pytest.raises(ValidationError):
        corpus.Record.model_validate(
            {
                **record,
                "expected": {**record["expected"], "validation_target": "http://example.com"},
            }
        )


def test_duplicate_content_cannot_be_hidden_by_renaming_identity():
    import copy

    original = corpus.validate()[0]
    renamed = copy.deepcopy(original)
    renamed.update(id="rg-" + "f" * 20, split="test")
    renamed["provenance"].update(repository_family="different", scenario_family="different")
    assert corpus.content_fingerprint(original) == corpus.content_fingerprint(renamed)
