"""Runs TradingAgents (github.com/TauricResearch/TradingAgents) for one stock.

TradingAgents analyses one ticker per run: market, news and fundamentals
analysts write reports, bull and bear researchers debate them, a trader and a
risk team review the plan, and a portfolio manager gives the final decision
with a five-level rating (Buy, Overweight, Hold, Underweight, Sell). We pass
the user's whole portfolio so the agents know what is already held.
"""

from __future__ import annotations

import hashlib
import threading

from . import confidence, reading, settings

_graph = None
_graph_lock = threading.Lock()

FAKE_RATINGS = ["Buy", "Overweight", "Hold", "Underweight", "Sell"]


def _build_graph():
    from tradingagents.default_config import build_default_config
    from tradingagents.graph.trading_graph import TradingAgentsGraph

    config = build_default_config()
    data = settings.DATA_DIR
    config.update({
        "llm_provider": "google",
        "quick_think_llm": settings.QUICK_MODEL,
        "deep_think_llm": settings.DEEP_MODEL,
        "max_debate_rounds": 1,
        "max_risk_discuss_rounds": 1,
        "output_language": "English",
        "results_dir": str(data / "logs"),
        "data_cache_dir": str(data / "cache"),
        "memory_log_path": str(data / "memory" / "trading_memory.md"),
    })
    return TradingAgentsGraph(selected_analysts=tuple(settings.ANALYSTS), config=config)


def _get_graph():
    global _graph
    with _graph_lock:
        if _graph is None:
            _graph = _build_graph()
        return _graph


def analyze_stock(ticker: str, trade_date: str, positions: list[dict]) -> dict:
    """Returns {"ticker", "rating", "decision", "reports", "confidencePercent",
    "confidenceLevel", "votes", "newsSummary", "sources"} for one stock.

    positions: [{"ticker", "quantity", "average_price"}] for the whole portfolio.
    The confidence fields say how much the Research Manager, the Trader and the
    final rating agree (confidence.py).
    """
    if settings.FAKE_AI:
        return _fake_analysis(ticker)

    from tradingagents.portfolio import PortfolioContext

    portfolio = PortfolioContext.model_validate({"currency": "USD", "positions": positions})
    graph = _get_graph()
    state, rating = graph.propagate(ticker, trade_date, portfolio=portfolio)
    reports = {
        "market": state.get("market_report", ""),
        "news": state.get("news_report", ""),
        "fundamentals": state.get("fundamentals_report", ""),
    }
    return {
        "ticker": ticker,
        "rating": rating,
        "decision": state.get("final_trade_decision", ""),
        "reports": reports,
        **confidence.stock_confidence(
            rating,
            state.get("investment_plan", ""),
            state.get("trader_investment_plan", ""),
            reports,
            settings.ANALYSTS,
        ),
        **reading.stock_reading(reports, settings.ANALYSTS,
                                (getattr(graph, "config", None) or {}).get("data_vendors")),
    }


# Fake mode makes the agents disagree by picking a rating on the other side of
# the market, so the confidence differs from stock to stock.
_OPPOSITE = {"Buy": "Sell", "Overweight": "Underweight", "Hold": "Buy",
             "Underweight": "Overweight", "Sell": "Buy"}
# The Trader only says Buy, Hold or Sell.
_TRADER_ACTION = {"Buy": "Buy", "Overweight": "Buy", "Hold": "Hold",
                  "Underweight": "Sell", "Sell": "Sell"}


def _fake_reports(ticker: str) -> dict:
    """Short made-up analyst reports. The news one is markdown, like the real one."""
    return {
        "market": f"(Fake mode) market report for {ticker}.",
        "news": (f"## News for {ticker}\n\n"
                 f"- **(Fake mode)** Example headline: {ticker} shares moved after the latest "
                 "quarterly update.\n"
                 "- Analysts mention steady demand and rising costs.\n"
                 "- Nothing in the news points to an unusual event this week.\n\n"
                 "| Topic | Tone |\n|---|---|\n| Earnings | Neutral |\n| Demand | Positive |"),
        "fundamentals": f"(Fake mode) fundamentals report for {ticker}.",
    }


def _fake_analysis(ticker: str) -> dict:
    digest = int(hashlib.sha256(ticker.encode()).hexdigest(), 16)
    rating = FAKE_RATINGS[digest % len(FAKE_RATINGS)]
    # 0 = all three agents agree, 1 = the trader differs, 2 = both others differ
    # (confidence High, Medium, Low), so the screens show all three levels.
    disagreement = digest // len(FAKE_RATINGS) % 3
    research = _OPPOSITE[rating] if disagreement == 2 else rating
    trader = _TRADER_ACTION[_OPPOSITE[rating] if disagreement >= 1 else rating]
    reports = _fake_reports(ticker)
    return {
        "ticker": ticker,
        "rating": rating,
        "decision": f"Rating: {rating}\n(Fake mode) Example decision text for {ticker}. "
                    "Set PIQ_FAKE_AI=0 and add a Gemini key to get the real TradingAgents analysis.",
        "reports": reports,
        **confidence.stock_confidence(
            rating,
            f"**Recommendation**: {research}\n\n**Rationale**: (Fake mode) example.",
            f"**Action**: {trader}\n\n**Reasoning**: (Fake mode) example.",
            reports,
            settings.ANALYSTS,
        ),
        **reading.stock_reading(reports, settings.ANALYSTS),
    }
