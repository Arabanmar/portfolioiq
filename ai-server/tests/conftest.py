import os
import sys
from pathlib import Path

# Tests never call Gemini: fake mode, and no token unless a test sets one.
os.environ["PIQ_FAKE_AI"] = "1"
os.environ["PIQ_SERVER_TOKEN"] = ""
# Not whatever the developer's ai-server/.env says (load_dotenv never overrides this).
os.environ["PIQ_ANALYSTS"] = "market,news,fundamentals"
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import pytest  # noqa: E402  (after the path and environment set-up above)


@pytest.fixture(autouse=True)
def isolated_data_dir(tmp_path, monkeypatch):
    """No test reads or writes the real ai-server/data folder, where saved results live."""
    from piq_server import settings

    monkeypatch.setattr(settings, "DATA_DIR", tmp_path)
