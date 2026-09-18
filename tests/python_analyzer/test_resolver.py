import ast

from python_analyzer_app.resolver import confidence_minimum, resolve_dependencies


def _handler(source: str) -> ast.FunctionDef:
    tree = ast.parse(source)
    return tree.body[0]


def test_repository_object_call_is_high_confidence_with_repository():
    handler = _handler("def get_user():\n    return user_repository.find_by_id(1)\n")
    resolution = resolve_dependencies(handler)

    assert resolution.confidence == "HIGH"
    assert not resolution.ambiguous
    assert resolution.primary == (None, "user_repository", "user")


def test_sqlalchemy_style_query_on_session_like_object_resolves_model():
    handler = _handler("def get_user():\n    return db.query(User).first()\n")
    resolution = resolve_dependencies(handler)

    assert resolution.confidence == "HIGH"
    assert resolution.primary.repository == "User"
    assert resolution.primary.resource == "User"


def test_unrelated_get_call_on_non_session_object_is_ignored():
    handler = _handler(
        'def read_header(request):\n    return request.headers.get("Authorization")\n'
    )
    resolution = resolve_dependencies(handler)

    assert resolution.paths == []
    assert not resolution.ambiguous
    assert resolution.confidence == "LOW"


def test_service_only_call_is_medium_confidence_without_repository():
    handler = _handler("def create_account(payload):\n    return account_service.create(payload)\n")
    resolution = resolve_dependencies(handler)

    assert resolution.confidence == "MEDIUM"
    assert resolution.primary == ("account_service", None, "account")


def test_dynamic_model_argument_on_session_call_is_flagged_ambiguous_not_guessed():
    handler = _handler("def get_thing():\n    return session.get(fetch_type())\n")
    resolution = resolve_dependencies(handler)

    assert resolution.ambiguous
    assert resolution.paths == []
    assert resolution.confidence == "LOW"


def test_no_matching_calls_falls_back_to_handler_name():
    handler = _handler("def list_public():\n    return []\n")
    resolution = resolve_dependencies(handler)

    assert resolution.paths == []
    assert resolution.confidence == "LOW"
    assert resolution.primary == (None, None, "list_public")


def test_call_inside_nested_function_is_not_direct():
    handler = _handler(
        "def outer():\n"
        "    def inner():\n"
        "        return user_repository.find_by_id(1)\n"
        "    return inner\n"
    )
    resolution = resolve_dependencies(handler)

    assert resolution.paths == []


def test_confidence_minimum_orders_low_below_medium_below_high():
    assert confidence_minimum("HIGH", "MEDIUM", "LOW") == "LOW"
    assert confidence_minimum("HIGH", "MEDIUM") == "MEDIUM"
    assert confidence_minimum("HIGH", "HIGH") == "HIGH"
