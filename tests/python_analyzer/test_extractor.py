from python_analyzer_app.extractor import (
    build_dependency_alias_catalog,
    extract_endpoints,
)

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


def test_handler_with_no_recognized_calls_falls_back_to_its_own_name():
    endpoints = extract_endpoints(SOURCE, "app/routes.py")
    for evidence in endpoints:
        assert evidence["endpoint"]["resource"] == evidence["endpoint"]["controller"]
        assert evidence["extraction_confidence"]["call_resolution"] == "LOW"
        assert evidence["dependency_paths"] == []


def test_repository_call_resolves_resource_and_high_call_confidence():
    source = """
from fastapi import FastAPI

app = FastAPI()


@app.get("/users/{user_id}")
def get_user(user_id: str):
    return user_repository.find_by_id(user_id)
"""
    endpoints = extract_endpoints(source, "app/users.py")
    endpoint = endpoints[0]["endpoint"]

    assert endpoint["resource"] == "user"
    assert endpoint["repository"] == "user_repository"
    assert endpoint["sensitivity"] == "HIGH"
    assert endpoints[0]["extraction_confidence"]["call_resolution"] == "HIGH"
    assert endpoints[0]["dependency_paths"] == [
        {
            "service": None,
            "repository": "user_repository",
            "resource": "user",
            "sensitivity": "HIGH",
        }
    ]


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


def test_router_relative_paths_are_normalized_to_absolute():
    source = (
        "from fastapi import APIRouter\n\n"
        "router = APIRouter()\n\n\n"
        '@router.get("")\n'
        "def list_items():\n"
        "    return []\n\n\n"
        '@router.post("items")\n'
        "def create_item():\n"
        "    return {}\n"
    )

    endpoints = extract_endpoints(source, "app/routes.py")

    assert sorted(e["endpoint"]["endpoint"] for e in endpoints) == ["/", "/items"]


def test_a_plain_dependency_injection_is_not_treated_as_authentication():
    """`Depends(get_db)` injects a session; counting it as auth silently
    suppressed the anonymous-reachability check on most FastAPI repos."""
    source = (
        "from fastapi import Depends, FastAPI\n"
        "from sqlalchemy.orm import Session\n\n"
        "app = FastAPI()\n\n\n"
        '@app.get("/customers")\n'
        "def list_customers(db: Session = Depends(get_db)):\n"
        "    return customer_repository.query(db)\n"
    )

    endpoint = extract_endpoints(source, "app/routes.py")[0]

    assert endpoint["endpoint"]["authentication"] is False
    assert endpoint["extraction_confidence"]["authorization"] == "LOW"


def test_annotated_dependency_is_recognised_as_authentication():
    """The modern `Annotated[..., Depends(...)]` form is not a parameter
    default, so inspecting defaults alone reported a protected route public."""
    source = (
        "from typing import Annotated\n"
        "from fastapi import Depends, FastAPI\n\n"
        "app = FastAPI()\n\n\n"
        '@app.get("/customers")\n'
        "def list_customers(user: Annotated[User, Depends(get_current_user)]):\n"
        "    return customer_repository.all()\n"
    )

    endpoint = extract_endpoints(source, "app/routes.py")[0]

    assert endpoint["endpoint"]["authentication"] is True
    assert endpoint["extraction_confidence"]["authorization"] == "MEDIUM"


def test_route_decorator_dependency_is_recognised_as_authentication():
    source = """
from fastapi import Depends, FastAPI

app = FastAPI()

@app.get("/admin", dependencies=[Depends(require_admin)])
def admin_dashboard():
    return {}
"""

    endpoint = extract_endpoints(source, "app/routes.py")[0]

    assert endpoint["endpoint"]["authentication"] is True
    assert endpoint["extraction_confidence"]["authorization"] == "MEDIUM"


def test_router_prefix_and_dependency_are_applied_to_routes():
    source = """
from fastapi import APIRouter, Depends

router = APIRouter(prefix="/accounts", dependencies=[Depends(get_current_user)])

@router.get("/{account_id}")
def account(account_id: str):
    return {}
"""

    endpoint = extract_endpoints(source, "app/accounts.py")[0]

    assert endpoint["endpoint"]["endpoint"] == "/accounts/{account_id}"
    assert endpoint["endpoint"]["authentication"] is True


def test_repository_mount_prefix_overrides_local_router_prefix():
    source = """
from fastapi import APIRouter

router = APIRouter(prefix="/local")

@router.get("/items")
def items():
    return []
"""

    endpoint = extract_endpoints(
        source, "app/items.py", router_prefixes={"router": ["/api/v1/local"]}
    )[0]

    assert endpoint["endpoint"]["endpoint"] == "/api/v1/local/items"


def test_named_annotated_dependency_alias_is_resolved_across_files():
    dependencies = """
from typing import Annotated
from fastapi import Depends
CurrentUser = Annotated[User, Depends(get_current_active_user)]
SessionDep = Annotated[Session, Depends(get_db)]
"""
    route = """
from fastapi import APIRouter
router = APIRouter()

@router.get("/me")
def read_me(current_user: CurrentUser, session: SessionDep):
    return current_user
"""
    aliases = build_dependency_alias_catalog(
        {"app/dependencies.py": dependencies, "app/routes.py": route}
    )

    endpoint = extract_endpoints(route, "app/routes.py", dependency_aliases=aliases)[0]

    assert endpoint["endpoint"]["authentication"] is True
    assert endpoint["extraction_confidence"]["authorization"] == "MEDIUM"


def test_duplicate_dependency_alias_is_unauthenticated_at_low_confidence():
    aliases = build_dependency_alias_catalog(
        {
            "one.py": "Current = Annotated[User, Depends(get_current_user)]",
            "two.py": "Current = Annotated[Config, Depends(get_settings)]",
        }
    )
    route = """
from fastapi import APIRouter
router = APIRouter()

@router.get("/ambiguous")
def ambiguous(value: Current):
    return value
"""

    endpoint = extract_endpoints(route, "routes.py", dependency_aliases=aliases)[0]

    assert endpoint["endpoint"]["authentication"] is False
    assert endpoint["extraction_confidence"]["authorization"] == "LOW"
