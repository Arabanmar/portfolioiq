"""Saved results of the TradingAgents analysis of a stock (Issue E), in plain Python (no AI).

One stock takes minutes of Gemini calls, so a finished analysis is saved to
<data>/cache/<date>/<TICKER>.json and reused instead of running the agents again.
(Fake mode saves to <TICKER>.fake.json, so a demo run never replaces a real result.)

A saved result is reused only when all of these match:
- the date: it was made today;
- the run key: your holdings, the settings of a run and the code that derives the
  saved fields. TradingAgents' Trader, Portfolio Manager and risk debators are told
  what you hold ("Current position in AAPL: 10 units ...", "Other positions: ..."),
  so a decision made for one portfolio is not a decision for another. The settings
  are fake or real mode, the analysts that are switched on and the two Gemini
  models. The code is a digest of the files that turn TradingAgents' output into
  the saved fields (confidence, news, debate ...), so editing one of them never
  serves fields made by the old version;
- the cache version (the layout of the file itself), and every field jobs.py and
  the app need, each of the right type.

Only a complete result is saved: a failed run saves nothing, and neither does one
with no readable rating, no decision, or an empty report from an analyst that is
switched on, so a retry can do better. Saving never fails an analysis: any problem
is logged and the analysis carries on.
"""

from __future__ import annotations

import hashlib
import json
import os
import re
import uuid
from datetime import datetime
from pathlib import Path

from . import settings
from .confidence import ANALYST_REPORTS, normalise_rating
from .text import as_text

# Bump when the layout of the saved FILE changes. (A change to what the code derives
# is caught by the code stamp in the run key, so it needs no bump.)
CACHE_VERSION = 1

# The same rule app.py applies to a ticker; repeated here so a file name can never
# hold a path separator or "..", whatever calls this. Matched whole, with ASCII digits.
_TICKER = re.compile(r"[A-Z][A-Z0-9.\-]{0,9}")
_DATE = re.compile(r"[0-9]{4}-[0-9]{2}-[0-9]{2}")
# Windows treats these names as devices even with an extension ("CON.json" is the
# console, not a file), so a stock with one of them is simply never saved.
_WINDOWS_DEVICE_NAMES = {"CON", "PRN", "AUX", "NUL",
                         *(f"COM{n}" for n in range(10)), *(f"LPT{n}" for n in range(10))}

# What agents.analyze_stock returns for a stock, apart from "fromCache".
REQUIRED_KEYS = ("ticker", "rating", "decision", "reports", "confidencePercent",
                 "confidenceLevel", "votes", "newsSummary", "sources", "bullCase", "bearCase")

# The code that produces the saved fields from TradingAgents' output.
_CODE_FILES = ("agents.py", "confidence.py", "debate.py", "reading.py", "text.py")


def code_stamp(folder: Path | None = None) -> str:
    """A digest of the code files that derive the saved fields.

    Any edit to one of them changes it, so results saved by older code are not
    served. A file that cannot be read still changes the digest (the server must
    start whatever happens).
    """
    folder = Path(__file__).parent if folder is None else Path(folder)
    digest = hashlib.sha256()
    for name in _CODE_FILES:
        try:
            digest.update(name.encode() + b"\0" + (folder / name).read_bytes() + b"\0")
        except OSError:
            digest.update(name.encode() + b"\0unreadable\0")
    return digest.hexdigest()[:12]


CODE_STAMP = code_stamp()


def run_key(positions: list[dict]) -> str:
    """A digest of what a result depends on: the holdings, the settings and the code.

    positions: [{"ticker", "quantity", "average_price"}], in any order. Numbers are
    rounded to 6 decimals so a rounding wobble in a merged holding is not a change.
    """
    book = sorted(
        ([str(p["ticker"]).upper(), round(float(p["quantity"]), 6),
          None if p.get("average_price") is None else round(float(p["average_price"]), 6)]
         for p in positions),
        key=lambda row: row[0])
    shape = {
        "fake": bool(settings.FAKE_AI),
        "analysts": sorted(settings.ANALYSTS),
        "models": [settings.QUICK_MODEL, settings.DEEP_MODEL],
        "code": CODE_STAMP,
    }
    text = json.dumps({"book": book, "shape": shape}, sort_keys=True)
    return hashlib.sha256(text.encode("utf-8")).hexdigest()[:16]


def _path(ticker: str, trade_date: str):
    if not _TICKER.fullmatch(str(ticker)) or not _DATE.fullmatch(str(trade_date)):
        return None
    if str(ticker).split(".")[0] in _WINDOWS_DEVICE_NAMES:
        return None
    suffix = ".fake.json" if settings.FAKE_AI else ".json"
    return settings.DATA_DIR / "cache" / trade_date / f"{ticker}{suffix}"


def _log(message: str) -> None:
    try:
        print(message, flush=True)
    except Exception:  # a closed or unencodable console must not lose an analysis
        pass


def is_complete(result: dict) -> bool:
    """Worth saving: a readable rating, a decision, and no empty report from an analyst switched on."""
    reports = result.get("reports")
    if not isinstance(reports, dict):
        return False
    if normalise_rating(result.get("rating")) is None or not as_text(result.get("decision")).strip():
        return False
    expected = [name for name in settings.ANALYSTS if name in ANALYST_REPORTS]
    return all(as_text(reports.get(name)).strip() for name in expected)


def _is_number(value) -> bool:
    return isinstance(value, (int, float)) and not isinstance(value, bool)


def _well_formed(result: dict) -> bool:
    """Every field is there with the type jobs.py and the app expect, and the result is complete."""
    return (
        _is_number(result["confidencePercent"])
        and isinstance(result["confidenceLevel"], str)
        and isinstance(result["votes"], dict)
        and isinstance(result["sources"], list)
        and all(isinstance(source, str) for source in result["sources"])
        and all(isinstance(result[name], str) for name in ("newsSummary", "bullCase", "bearCase"))
        and is_complete(result)
    )


def load(ticker: str, trade_date: str, key: str) -> dict | None:
    """The saved result for this stock, day and run key, or None.

    None also for a file that is missing, unreadable, half-written, from another
    version, day, stock or run, or whose result lacks a field, has a wrong type or
    is not complete.
    """
    path = _path(ticker, trade_date)
    if path is None or not path.is_file():
        return None
    try:
        entry = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None
    if (not isinstance(entry, dict)
            or entry.get("version") != CACHE_VERSION
            or entry.get("ticker") != ticker
            or entry.get("tradeDate") != trade_date
            or entry.get("runKey") != key):
        return None
    result = entry.get("result")
    if (not isinstance(result, dict) or result.get("ticker") != ticker
            or any(name not in result for name in REQUIRED_KEYS)
            or not _well_formed(result)):
        return None
    return result


def save(ticker: str, trade_date: str, key: str, result: dict) -> bool:
    """Saves a complete result (replacing an earlier one for this stock and day).

    Returns whether it was saved. The file is written whole under a unique name and
    then renamed into place, so a crash never leaves a half-written entry, and two
    writers never share a temporary file. Whatever goes wrong is logged, never raised:
    an analysis that took minutes must not be lost to a cache problem.
    """
    path = _path(ticker, trade_date)
    if path is None or not is_complete(result):
        return False
    temporary = None
    try:
        entry = {
            "version": CACHE_VERSION,
            "ticker": ticker,
            "tradeDate": trade_date,
            "runKey": key,
            "savedAt": datetime.now().isoformat(timespec="seconds"),
            "result": result,
        }
        text = json.dumps(entry)
        path.parent.mkdir(parents=True, exist_ok=True)
        temporary = path.with_name(f"{path.name}.{uuid.uuid4().hex[:8]}.tmp")
        temporary.write_text(text, encoding="utf-8")
        os.replace(temporary, path)
    except Exception as exc:  # noqa: BLE001 - see the docstring
        # {exc!a} keeps the line plain ASCII, whatever characters a path holds.
        _log(f"Could not save the result for {ticker} ({type(exc).__name__}: {exc!a}).")
        if temporary is not None:
            try:
                temporary.unlink()
            except OSError:
                pass
        return False
    return True
