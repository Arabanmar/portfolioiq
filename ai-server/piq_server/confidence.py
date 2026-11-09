"""How much the TradingAgents team agrees with itself, in plain Python (no AI).

Same rule as metrics.py: this number is calculated in code and the summary
model only receives it. It measures how much the agents AGREE, not the chance
of making money: a stock all the agents like can still lose value.

Three agents give a verdict on each stock: the Research Manager (a five-level
recommendation), the Trader (Buy, Hold or Sell) and the Portfolio Manager (the
final five-level rating). Their votes are compared by direction.
"""

from __future__ import annotations

import math
import re

from .metrics import round2

RATINGS = ("Buy", "Overweight", "Hold", "Underweight", "Sell")
_CANONICAL = {rating.lower(): rating for rating in RATINGS}
DIRECTION = {
    "Buy": "up",
    "Overweight": "up",
    "Hold": "neutral",
    "Underweight": "down",
    "Sell": "down",
}

# Score by how many of the three votes (the final rating plus the Research
# Manager's and the Trader's) point the same way as the final rating.
AGREEMENT_SCORE = {3: 90.0, 2: 65.0, 1: 40.0}
EMPTY_REPORT_PENALTY = 10.0
MIN_CONFIDENCE = 10.0
MAX_CONFIDENCE = 95.0
HIGH_FROM = 75.0
MEDIUM_FROM = 50.0

# The analyst reports agents.py keeps. A report only counts against the score
# when that analyst was switched on (settings.ANALYSTS).
ANALYST_REPORTS = ("market", "news", "fundamentals")

# TradingAgents writes these labels as the first line of the Research Manager's
# plan and the Trader's proposal (agents/schemas.py in TradingAgents). When its
# structured output fails the model writes free text instead, so a label only
# counts at the start of a line (after list or quote marks), never in the
# middle of a sentence, and its value must be on the same line.
_LINE_START = r"^[ \t>#*\-\d.)]*"
_RECOMMENDATION = re.compile(
    _LINE_START + r"\*{0,2}Recommendation\*{0,2}[ \t]*:[ \t]*\*{0,2}[ \t]*"
    r"(Buy|Overweight|Hold|Underweight|Sell)\b",
    re.IGNORECASE | re.MULTILINE)
_ACTION = re.compile(
    _LINE_START + r"\*{0,2}Action\*{0,2}[ \t]*:[ \t]*\*{0,2}[ \t]*(Buy|Hold|Sell)\b",
    re.IGNORECASE | re.MULTILINE)


def _as_text(value) -> str:
    """Plain text from a model reply: a string, or (for some providers) a list of content parts."""
    if value is None:
        return ""
    if isinstance(value, str):
        return value
    if isinstance(value, (list, tuple)):
        return "\n".join(_as_text(part.get("text") if isinstance(part, dict) else part)
                         for part in value)
    return str(value)


def normalise_rating(rating) -> str | None:
    """'buy ' -> 'Buy'; anything that is not one of the five ratings -> None."""
    return _CANONICAL.get(_as_text(rating).strip().lower())


def _find(pattern: re.Pattern, text) -> str | None:
    match = pattern.search(_as_text(text))
    return _CANONICAL[match.group(1).lower()] if match else None


def parse_research_vote(text) -> str | None:
    """The Research Manager's recommendation, or None if the plan has no readable one."""
    return _find(_RECOMMENDATION, text)


def parse_trader_vote(text) -> str | None:
    """The Trader's action (Buy, Hold or Sell), or None if the proposal has no readable one."""
    return _find(_ACTION, text)


def clamp_confidence(value: float) -> float:
    return max(MIN_CONFIDENCE, min(MAX_CONFIDENCE, value))


def level_for(percent: float) -> str:
    return "High" if percent >= HIGH_FROM else "Medium" if percent >= MEDIUM_FROM else "Low"


def stock_confidence(final_rating, research_text, trader_text, reports: dict,
                     enabled_analysts) -> dict:
    """Confidence for one stock.

    An unreadable vote counts as not agreeing. If the final rating itself is
    unreadable, nothing can agree with it, so the stock scores as 1 of 3.
    """
    final = normalise_rating(final_rating)
    research = parse_research_vote(research_text)
    trader = parse_trader_vote(trader_text)

    agreeing = 1
    if final is not None:
        for vote in (research, trader):
            if vote is not None and DIRECTION[vote] == DIRECTION[final]:
                agreeing += 1

    expected = [name for name in enabled_analysts if name in ANALYST_REPORTS]
    empty = sum(1 for name in expected if not _as_text(reports.get(name)).strip())

    percent = round2(clamp_confidence(AGREEMENT_SCORE[agreeing] - EMPTY_REPORT_PENALTY * empty))
    return {
        "confidencePercent": percent,
        "confidenceLevel": level_for(percent),
        "votes": {"researchManager": research, "trader": trader, "final": final},
    }


def portfolio_confidence(stocks: list[dict], weights: dict[str, float]) -> dict | None:
    """The stocks' confidence averaged by how much of the portfolio each one is.

    weights: ticker -> share of the portfolio (any positive numbers).
    The average is rounded to a whole number (half up) before the level is
    chosen, so the percent the app shows and the level always agree.
    None if no stock has a weight to count.
    """
    pairs = [(weights.get(s["ticker"], 0.0), s["confidencePercent"])
             for s in stocks if "confidencePercent" in s]
    total = sum(weight for weight, _ in pairs)
    if not pairs or total <= 0:
        return None
    average = clamp_confidence(sum(weight * value for weight, value in pairs) / total)
    percent = float(math.floor(round2(average) + 0.5))
    return {"confidencePercent": percent, "confidenceLevel": level_for(percent)}
