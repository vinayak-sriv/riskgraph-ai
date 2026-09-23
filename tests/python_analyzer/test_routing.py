from python_analyzer_app.routing import resolve_router_prefixes


def test_stitches_nested_router_prefixes_across_explicit_imports():
    sources = {
        "app/main.py": """
from fastapi import FastAPI
from app.api import api_router
app = FastAPI()
app.include_router(api_router, prefix="/api")
""",
        "app/api/__init__.py": """
from fastapi import APIRouter
from .routes import items
api_router = APIRouter(prefix="/v1")
api_router.include_router(items.router, prefix="/items")
""",
        "app/api/routes/items.py": """
from fastapi import APIRouter
router = APIRouter(prefix="/catalog")
""",
    }

    prefixes = resolve_router_prefixes(sources)

    assert prefixes["app/api/routes/items.py"]["router"] == ["/api/v1/items/catalog"]


def test_unmounted_router_keeps_its_local_prefix():
    prefixes = resolve_router_prefixes(
        {"routes.py": 'from fastapi import APIRouter\nrouter = APIRouter(prefix="/local")'}
    )

    assert prefixes["routes.py"]["router"] == ["/local"]
