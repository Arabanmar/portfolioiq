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

from . import settings

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
    """Returns {"ticker", "rating", "decision", "reports"} for one stock.

    positions: [{"ticker", "quantity", "average_price"}] for the whole portfolio.
    """
    if settings.FAKE_AI:
        return _fake_analysis(ticker)

    from tradingagents.portfolio import PortfolioContext

    portfolio = PortfolioContext.model_validate({"currency": "USD", "positions": positions})
    state, rating = _get_graph().propagate(ticker, trade_date, portfolio=portfolio)
    return {
        "ticker": ticker,
        "rating": rating,
        "decision": state.get("final_trade_decision", ""),
        "reports": {
            "market": state.get("market_report", ""),
            "news": state.get("news_report", ""),
            "fundamentals": state.get("fundamentals_report", ""),
        },
    }


def _fake_analysis(ticker: str) -> dict:
    rating = FAKE_RATINGS[int(hashlib.sha256(ticker.encode()).hexdigest(), 16) % len(FAKE_RATINGS)]
    return {
        "ticker": ticker,
        "rating": rating,
        "decision": f"Rating: {rating}\n(Fake mode) Example decision text for {ticker}. "
                    "Set PIQ_FAKE_AI=0 and add a Gemini key to get the real TradingAgents analysis.",
        "reports": {"market": "", "news": "", "fundamentals": ""},
    }
