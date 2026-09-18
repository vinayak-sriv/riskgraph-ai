from python_analyzer_app.sensitivity_policy import SensitivityPolicy


def _policy() -> SensitivityPolicy:
    return SensitivityPolicy()


def test_exact_match_is_case_insensitive():
    classification = _policy().classify("Account")
    assert classification.sensitivity == "HIGH"
    assert classification.match_kind == "exact"
    assert not classification.defaulted


def test_prefix_match_applies_to_unlisted_resource():
    classification = _policy().classify("AccountBalance")
    assert classification.sensitivity == "HIGH"
    assert classification.match_kind == "prefix"


def test_suffix_match_applies_to_unlisted_resource():
    classification = _policy().classify("ApiTokens")
    assert classification.sensitivity == "CRITICAL"
    assert classification.match_kind == "suffix"


def test_exact_beats_prefix_when_both_match():
    # "Account" matches its own exact rule and its own prefix rule; exact wins.
    classification = _policy().classify("Account")
    assert classification.match_kind == "exact"


def test_unmatched_resource_defaults_to_default_sensitivity():
    classification = _policy().classify("Widget")
    assert classification.sensitivity == "MODERATE"
    assert classification.defaulted


def test_unmatched_repository_backed_resource_uses_repository_default():
    classification = _policy().classify("Widget", repository_backed=True)
    assert classification.sensitivity == "MEDIUM"
    assert classification.defaulted


def test_blank_resource_raises():
    try:
        _policy().classify("")
    except ValueError:
        return
    raise AssertionError("expected ValueError for blank resource")
