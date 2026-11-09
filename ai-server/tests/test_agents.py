import pytest

from piq_server import agents, settings

POSITIONS = [{"ticker": "AAPL", "quantity": 1, "average_price": 100}]
ALL_ANALYSTS = ["market", "news", "fundamentals"]


class StandInGraph:
    """Stands in for TradingAgentsGraph: hands back a ready-made state."""

    def __init__(self, state, rating, config=None):
        self.state = state
        self.rating = rating
        self.config = config
        self.calls = []

    def propagate(self, ticker, trade_date, portfolio=None):
        self.calls.append((ticker, trade_date))
        return self.state, self.rating


def real_mode(monkeypatch, state, rating="Buy", config=None):
    graph = StandInGraph(state, rating, config)
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


# ---- what the agents read (Issue C) ----

DEFAULT_SOURCES = ["Yahoo Finance prices and indicators",
                   "Yahoo Finance news, past 7 days",
                   "SEC EDGAR financial statements"]
LONG_NEWS = "## Headline\n\n" + "- **Shares** rose on [strong demand](https://example.com).\n" * 40


def test_real_mode_reads_the_news_summary_and_the_sources(monkeypatch):
    real_mode(monkeypatch, full_state(news_report=LONG_NEWS))
    result = agents.analyze_stock("AAPL", "2026-11-09", POSITIONS)

    summary = result["newsSummary"]
    assert 0 < len(summary) <= 700
    assert summary.endswith("...")
    assert summary.startswith("Headline\nShares rose on strong demand.")
    for mark in ("#", "*", "[", "](", "https"):
        assert mark not in summary
    assert result["sources"] == DEFAULT_SOURCES


def test_a_short_news_report_is_kept_whole(monkeypatch):
    real_mode(monkeypatch, full_state(news_report="**Quiet** week for the company."))
    assert agents.analyze_stock("AAPL", "2026-11-09", POSITIONS)["newsSummary"] == \
        "Quiet week for the company."


def test_a_news_report_given_as_content_parts_is_read(monkeypatch):
    real_mode(monkeypatch, full_state(news_report=[{"type": "text", "text": "## Big news\n\nShares rose."}]))
    assert agents.analyze_stock("AAPL", "2026-11-09", POSITIONS)["newsSummary"] == "Big news\nShares rose."


def test_no_news_report_gives_no_summary_and_no_news_source(monkeypatch):
    real_mode(monkeypatch, full_state(news_report=""))
    result = agents.analyze_stock("AAPL", "2026-11-09", POSITIONS)

    assert result["newsSummary"] == ""
    assert result["sources"] == [DEFAULT_SOURCES[0], DEFAULT_SOURCES[2]]


def test_the_sources_follow_the_graphs_configured_vendors(monkeypatch):
    config = {"data_vendors": {"news_data": "alpha_vantage", "fundamental_data": "yfinance"}}
    real_mode(monkeypatch, full_state(), config=config)

    assert agents.analyze_stock("AAPL", "2026-11-09", POSITIONS)["sources"] == [
        "Yahoo Finance prices and indicators",
        "Alpha Vantage news, past 7 days",
        "Yahoo Finance financial statements",
    ]


def test_switched_off_analysts_have_no_source(monkeypatch):
    real_mode(monkeypatch, full_state())
    monkeypatch.setattr(settings, "ANALYSTS", ["market", "news"])
    assert agents.analyze_stock("AAPL", "2026-11-09", POSITIONS)["sources"] == DEFAULT_SOURCES[:2]


def test_fake_mode_fills_the_news_summary_and_the_sources():
    assert settings.FAKE_AI  # tests/conftest.py turns fake mode on
    result = agents.analyze_stock("AAPL", "2026-11-09", POSITIONS)

    summary = result["newsSummary"]
    assert 0 < len(summary) <= 700
    assert summary.startswith("News for AAPL\n")
    for mark in ("#", "*", "|", "---"):
        assert mark not in summary
    assert result["sources"] == DEFAULT_SOURCES
