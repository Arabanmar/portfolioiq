"""Server settings, read from environment variables (and the .env file next to app.py)."""

from __future__ import annotations

import os
from pathlib import Path

from dotenv import load_dotenv

SERVER_DIR = Path(__file__).resolve().parent.parent
load_dotenv(SERVER_DIR / ".env")


def _flag(name: str) -> bool:
    return os.getenv(name, "").strip().lower() in ("1", "true", "yes", "on")


# Fake mode answers instantly with made-up ratings and text, without calling
# Gemini. Used by the automated tests and for trying the app with no key.
FAKE_AI = _flag("PIQ_FAKE_AI")

GOOGLE_API_KEY = os.getenv("GOOGLE_API_KEY", "").strip()

# Gemini models. The quick model runs the analysts, researchers and trader
# (most of the calls); the deep model runs the two manager agents and writes
# the portfolio summary and chat answers.
QUICK_MODEL = os.getenv("PIQ_QUICK_MODEL", "gemini-3.5-flash-lite").strip()
DEEP_MODEL = os.getenv("PIQ_DEEP_MODEL", "gemini-3.8-flash").strip()

# TradingAgents analysts to run per stock. "social" (StockTwits/Reddit) is
# off by default to keep each stock's analysis faster and cheaper.
ANALYSTS = [a.strip() for a in os.getenv("PIQ_ANALYSTS", "market,news,fundamentals").split(",") if a.strip()]

# Optional shared secret. When set, every request except /health must send
# the same value in the X-PIQ-Token header (the app reads it from local.properties).
SERVER_TOKEN = os.getenv("PIQ_SERVER_TOKEN", "").strip()

MAX_HOLDINGS = int(os.getenv("PIQ_MAX_HOLDINGS", "10"))

DATA_DIR = SERVER_DIR / "data"
