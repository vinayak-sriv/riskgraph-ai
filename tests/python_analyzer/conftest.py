import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVICE_ROOT = ROOT / "services" / "python-analyzer"
sys.path.insert(0, str(SERVICE_ROOT))
