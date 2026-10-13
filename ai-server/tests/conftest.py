import os
import sys
from pathlib import Path

# Tests never call Gemini: fake mode, and no token unless a test sets one.
os.environ["PIQ_FAKE_AI"] = "1"
os.environ["PIQ_SERVER_TOKEN"] = ""
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
