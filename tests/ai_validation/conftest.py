import importlib.util
import sys
from pathlib import Path

path = Path(__file__).resolve().parents[2] / "services/ai-validation-service/app"
spec = importlib.util.spec_from_file_location(
    "ai_app", path / "__init__.py", submodule_search_locations=[str(path)]
)
module = importlib.util.module_from_spec(spec)
sys.modules["ai_app"] = module
spec.loader.exec_module(module)
