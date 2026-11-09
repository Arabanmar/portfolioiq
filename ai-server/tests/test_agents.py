import pytest

from piq_server import agents, settings

POSITIONS = [{"ticker": "AAPL", "quantity": 1, "average_price": 100}]
ALL_ANALYSTS = ["market", "news", "fundamentals"]


class StandInGraph:
    """Stands in for TradingAgentsGraph: hands back a ready-made state."""

    def __init__(self, state, rating):
        self.state = state
        self.rating = rating
        self.calls = []

    def propagate(self, ticker, trade_date, portfolio=None):
        self.calls.append((ticker, trade_date))
        return self.state, self.rating


def real_mode(monkeypatch, state, rating="Buy"):
    graph = StandInGraph(state, rating)
    monkeypatch.setattr(settings, "FAKE_AI", False)
    monkeypatch.setattr(settings, "ANALYSTS", ALL_ANALYSTS)
    monkeypatch.setattr(agents, "_get_graph", lambda: graph)
    return graph


def full_state(**changes):
    state = {
        "market_report": "market text",
        "news_report": "news text",
        "fundamentals_report": "fundamentals text",
        "investment_plan": "**Recommendation**: Buy\n\n**Rationale**: r",
        "trader_investment_plan": "**Action**: Buy\n\n**Reasoning**: r",
        "final_trade_decision": "**Rating**: Buy\n\n**Executive Summary**: s",
    }
    state.update(changes)
    return state


# ---- real mode, with a stand-in for the TradingAgents graph ----

def test_real_mode_reads_the_votes_and_reports_from_the_state(monkeypatch):
    graph = real_mode(monkeypatch, full_state())
    result = agents.analyze_stock("AAPL", "2026-11-09", POSITIONS)

    assert graph.calls == [("AAPL", "2026-11-09")]
    assert result["ticker"] == "AAPL"
    assert result["rating"] == "Buy"
    assert result["decision"].startswith("**Rating**: Buy")
    assert result["reports"] == {"market": "market text", "news": "news text",
                                 "fundamentals": "fundamentals text"}
    assert result["confidencePercent"] == 90
    assert result["confidenceLevel"] == "High"
    assert result["votes"] == {"researchManager": "Buy", "trader": "Buy", "final": "Buy"}


def test_a_disagreeing_trader_and_an_empty_news_report_lower_the_confidence(monkeypatch):
    real_mode(monkeypatch, full_state(trader_investment_plan="**Action**: Sell\n\n**Reasoning**: r",
                                      news_report=""))
    result = agents.analyze_stock("AAPL", "2026-11-09", POSITIONS)

    assert result["confidencePercent"] == 55  # 2 of 3 = 65, minus 10 for the empty news report
    assert result["confidenceLevel"] == "Medium"
    assert result["votes"]["trader"] == "Sell"


def test_a_state_without_the_plans_still_returns_the_analysis(monkeypatch):
    state = full_state()
    del state["investment_plan"]
    del state["trader_investment_plan"]
    real_mode(monkeypatch, state)
    result = agents.analyze_stock("AAPL", "2026-11-09", POSITIONS)

    assert result["decision"].startswith("**Rating**: Buy")
    assert result["confidencePercent"] == 40
    assert result["confidenceLevel"] == "Low"
    assert result["votes"] == {"researchManager": None, "trader": None, "final": "Buy"}


def test_switched_off_analysts_do_not_lower_the_confidence(monkeypatch):
    real_mode(monkeypatch, full_state(fundamentals_report=""))
    monkeypatch.setattr(settings, "ANALYSTS", ["market", "news"])
    assert agents.analyze_stock("AAPL", "2026-11-09", POSITIONS)["confidencePercent"] == 90


# ---- fake mode (what the tests and the no-key demo use) ----

@pytest.mark.parametrize("ticker, rating, percent, level, research, trader", [
    ("TSLA", "Sell", 90, "High", "Sell", "Sell"),
    ("AAPL", "Overweight", 65, "Medium", "Overweight", "Sell"),
    ("JNJ", "Buy", 40, "Low", "Sell", "Sell"),
])
def test_fake_mode_shows_high_medium_and_low_confidence(ticker, rating, percent, level, research, trader):
    assert settings.FAKE_AI  # tests/conftest.py turns fake mode on
    result = agents.analyze_stock(ticker, "2026-11-09", POSITIONS)

    assert result["rating"] == rating
    assert result["confidencePercent"] == percent
    assert result["confidenceLevel"] == level
    assert result["votes"] == {"researchManager": research, "trader": trader, "final": rating}


def test_fake_reports_are_not_empty():
    reports = agents.analyze_stock("AAPL", "2026-11-09", POSITIONS)["reports"]
    assert set(reports) == {"market", "news", "fundamentals"}
    assert all(text.strip() for text in reports.values())
