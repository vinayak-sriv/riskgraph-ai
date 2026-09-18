from python_analyzer_app.extractor import extract_endpoints

SOURCE = """
from fastapi import Depends, FastAPI

app = FastAPI()


def get_current_user():
    ...


@app.get("/public")
def list_public():
    return []


@app.post("/accounts/{account_id}")
def create_account(account_id: str, user=Depends(get_current_user)):
    return {}


@app.on_event("startup")
def startup():
    ...
"""


def test_extracts_route_method_path_and_controller():
    endpoints = extract_endpoints(SOURCE, "app/routes.py")
    by_controller = {e["endpoint"]["controller"]: e for e in endpoints}

    assert set(by_controller) == {"list_public", "create_account"}
    public = by_controller["list_public"]["endpoint"]
    assert public["method"] == "GET"
    assert public["endpoint"] == "/public"


def test_depends_parameter_marks_authentication_true():
    endpoints = extract_endpoints(SOURCE, "app/routes.py")
    by_controller = {e["endpoint"]["controller"]: e for e in endpoints}

    assert by_controller["create_account"]["endpoint"]["authentication"] is True
    assert by_controller["list_public"]["endpoint"]["authentication"] is False


def test_unresolved_call_resolution_is_flagged_low_confidence():
    endpoints = extract_endpoints(SOURCE, "app/routes.py")
    for evidence in endpoints:
        assert evidence["endpoint"]["resource"] == "unresolved"
        assert evidence["extraction_confidence"]["call_resolution"] == "LOW"


def test_custom_router_variable_name_is_detected():
    source = """
from fastapi import APIRouter

accounts_router = APIRouter()


@accounts_router.delete("/accounts/{account_id}")
def delete_account(account_id: str):
    return None
"""
    endpoints = extract_endpoints(source, "app/accounts.py")
    assert len(endpoints) == 1
    assert endpoints[0]["endpoint"]["method"] == "DELETE"


def test_syntax_error_returns_no_endpoints_instead_of_raising():
    assert extract_endpoints("def broken(:", "app/broken.py") == []
