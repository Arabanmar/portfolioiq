import json

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
        "investment_debate_state": {
            "bull_history": "\nBull Analyst: **Growth is strong.** Shares have room to run.",
            "bear_history": "\nBear Analyst: **Costs are rising.** Demand is slowing.",
        },
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


# ---- the case for and against (Issue D) ----

def test_real_mode_reads_both_sides_of_the_debate(monkeypatch):
    real_mode(monkeypatch, full_state())
    result = agents.analyze_stock("AAPL", "2026-11-09", POSITIONS)

    assert result["bullCase"] == "Growth is strong. Shares have room to run."
    assert result["bearCase"] == "Costs are rising. Demand is slowing."


def test_a_long_debate_is_cut_to_500_characters_per_side(monkeypatch):
    long_bull = "\nBull Analyst: ## Case\n\n" + "- **Shares** rose on strong demand.\n" * 40
    debate = {"bull_history": long_bull, "bear_history": "\nBear Analyst: Short."}
    real_mode(monkeypatch, full_state(investment_debate_state=debate))
    result = agents.analyze_stock("AAPL", "2026-11-09", POSITIONS)

    assert 0 < len(result["bullCase"]) <= 500
    assert result["bullCase"].endswith("...")
    assert result["bullCase"].startswith("Case\nShares rose on strong demand.")
    for mark in ("#", "*", "Bull Analyst"):
        assert mark not in result["bullCase"]
    assert result["bearCase"] == "Short."


def test_only_the_first_round_of_the_debate_is_shown(monkeypatch):
    debate = {"bull_history": "\nBull Analyst: Round one.\nBull Analyst: Round two.",
              "bear_history": "\nBear Analyst: First reply.\nBear Analyst: Second reply."}
    real_mode(monkeypatch, full_state(investment_debate_state=debate))
    result = agents.analyze_stock("AAPL", "2026-11-09", POSITIONS)

    assert result["bullCase"] == "Round one."
    assert result["bearCase"] == "First reply."


def test_a_state_without_a_debate_still_returns_the_analysis(monkeypatch):
    state = full_state()
    del state["investment_debate_state"]
    real_mode(monkeypatch, state)
    result = agents.analyze_stock("AAPL", "2026-11-09", POSITIONS)

    assert result["bullCase"] == ""
    assert result["bearCase"] == ""
    assert result["decision"].startswith("**Rating**: Buy")


def test_fake_mode_fills_both_sides_of_the_debate():
    assert settings.FAKE_AI  # tests/conftest.py turns fake mode on
    result = agents.analyze_stock("AAPL", "2026-11-09", POSITIONS)

    for side in ("bullCase", "bearCase"):
        assert 0 < len(result[side]) <= 500
        for mark in ("#", "*", "Analyst:"):
            assert mark not in result[side]
    assert result["bullCase"].startswith("(Fake mode) The case for AAPL.")
    assert result["bearCase"].startswith("(Fake mode) The case against AAPL.")
    assert "Customers keep coming back" in result["bullCase"]
    assert "Competition is getting stronger" in result["bearCase"]


# ---- saved results (Issue E) ----

DAY = "2026-11-09"


def analyze(trade_date=DAY, positions=None):
    return agents.analyze_stock("AAPL", trade_date, positions or POSITIONS)


def without_flag(result):
    return {key: value for key, value in result.items() if key != "fromCache"}


class FailingGraph:
    def propagate(self, ticker, trade_date, portfolio=None):
        raise RuntimeError("the AI service's free limit was reached")


def must_not_run():
    raise AssertionError("the agents must not run again")


def test_a_second_analysis_the_same_day_does_not_run_the_agents_again(monkeypatch):
    graph = real_mode(monkeypatch, full_state())
    first = analyze()
    second = analyze()

    assert len(graph.calls) == 1
    assert first["fromCache"] is False
    assert second["fromCache"] is True
    assert without_flag(second) == without_flag(first)


def test_the_saved_result_is_used_without_even_building_the_agents(monkeypatch):
    real_mode(monkeypatch, full_state())
    first = analyze()
    monkeypatch.setattr(agents, "_get_graph", must_not_run)

    assert without_flag(analyze()) == without_flag(first)


def test_a_new_day_runs_the_agents_again(monkeypatch):
    graph = real_mode(monkeypatch, full_state())
    analyze("2026-11-09")
    next_day = analyze("2026-11-10")
    back_to_the_first_day = analyze("2026-11-09")

    assert next_day["fromCache"] is False
    assert len(graph.calls) == 2
    assert back_to_the_first_day["fromCache"] is True  # each day keeps its own file
    assert len(graph.calls) == 2


def test_a_failed_run_is_not_saved(monkeypatch, tmp_path):
    monkeypatch.setattr(settings, "FAKE_AI", False)
    monkeypatch.setattr(settings, "ANALYSTS", ALL_ANALYSTS)
    monkeypatch.setattr(agents, "_get_graph", lambda: FailingGraph())
    with pytest.raises(RuntimeError):
        analyze()
    assert not (tmp_path / "cache").exists()
    assert agents.has_saved_result("AAPL", DAY, POSITIONS) is False

    graph = real_mode(monkeypatch, full_state())  # the retry
    retry = analyze()

    assert retry["fromCache"] is False
    assert len(graph.calls) == 1


def test_a_result_without_a_decision_is_not_saved(monkeypatch):
    graph = real_mode(monkeypatch, full_state(final_trade_decision=""))
    first = analyze()
    second = analyze()

    assert first["fromCache"] is False and second["fromCache"] is False
    assert len(graph.calls) == 2


def test_a_result_without_a_readable_rating_is_not_saved(monkeypatch):
    graph = real_mode(monkeypatch, full_state(), rating="REVIEW")
    analyze()
    analyze()

    assert len(graph.calls) == 2


def test_a_changed_portfolio_runs_the_agents_again(monkeypatch):
    graph = real_mode(monkeypatch, full_state())
    analyze()
    more_shares = [{"ticker": "AAPL", "quantity": 5, "average_price": 100}]
    another_stock = POSITIONS + [{"ticker": "MSFT", "quantity": 1, "average_price": 300}]

    assert analyze(positions=more_shares)["fromCache"] is False
    assert analyze(positions=another_stock)["fromCache"] is False
    assert len(graph.calls) == 3
    assert analyze(positions=another_stock)["fromCache"] is True  # the newest run is the one kept
    assert len(graph.calls) == 3


def test_changed_settings_run_the_agents_again(monkeypatch):
    graph = real_mode(monkeypatch, full_state())
    analyze()

    monkeypatch.setattr(settings, "ANALYSTS", ["market", "news"])
    assert analyze()["fromCache"] is False
    monkeypatch.setattr(settings, "QUICK_MODEL", "another-quick-model")
    assert analyze()["fromCache"] is False
    assert len(graph.calls) == 3


def test_a_fake_result_is_never_served_to_a_real_run(monkeypatch):
    assert settings.FAKE_AI  # tests/conftest.py turns fake mode on
    fake = analyze()
    assert fake["decision"].startswith("Rating:")

    graph = real_mode(monkeypatch, full_state())
    real = analyze()

    assert real["fromCache"] is False
    assert len(graph.calls) == 1
    assert real["decision"].startswith("**Rating**: Buy")


def test_fake_mode_reuses_a_saved_result_too():
    first = analyze()
    second = analyze()

    assert first["fromCache"] is False
    assert second["fromCache"] is True
    assert without_flag(second) == without_flag(first)


def test_has_saved_result_follows_the_same_rules(monkeypatch):
    real_mode(monkeypatch, full_state())
    assert agents.has_saved_result("AAPL", DAY, POSITIONS) is False
    analyze()

    assert agents.has_saved_result("AAPL", DAY, POSITIONS) is True
    assert agents.has_saved_result("AAPL", "2026-11-10", POSITIONS) is False
    assert agents.has_saved_result("AAPL", DAY, [{"ticker": "AAPL", "quantity": 99, "average_price": 1}]) is False
    assert agents.has_saved_result("MSFT", DAY, POSITIONS) is False


def test_an_analysis_still_works_when_the_result_cannot_be_saved(monkeypatch, tmp_path):
    graph = real_mode(monkeypatch, full_state())
    blocker = tmp_path / "blocker"
    blocker.write_text("a file, not a folder", encoding="utf-8")
    monkeypatch.setattr(settings, "DATA_DIR", blocker)

    first = analyze()
    second = analyze()

    assert first["fromCache"] is False and second["fromCache"] is False
    assert first["rating"] == "Buy"
    assert len(graph.calls) == 2


def test_a_saved_result_from_older_code_is_not_served(monkeypatch, tmp_path):
    graph = real_mode(monkeypatch, full_state())
    analyze()
    path = tmp_path / "cache" / DAY / "AAPL.json"
    entry = json.loads(path.read_text(encoding="utf-8"))
    del entry["result"]["bullCase"]  # as if saved before the debate was added
    path.write_text(json.dumps(entry), encoding="utf-8")

    assert analyze()["fromCache"] is False
    assert len(graph.calls) == 2


def test_the_saved_result_keeps_every_field_the_screen_needs(monkeypatch):
    real_mode(monkeypatch, full_state(news_report=LONG_NEWS))
    first = analyze()
    second = analyze()

    for key in ("rating", "decision", "confidencePercent", "confidenceLevel", "votes",
                "newsSummary", "sources", "bullCase", "bearCase", "reports"):
        assert second[key] == first[key]
    assert second["fromCache"] is True


def test_a_run_where_an_analyst_came_back_empty_is_not_saved(monkeypatch):
    graph = real_mode(monkeypatch, full_state(fundamentals_report=""))
    analyze()
    analyze()

    assert len(graph.calls) == 2  # the empty report is a defect, so a retry gets another go


def test_an_empty_report_from_an_analyst_that_is_switched_off_does_not_stop_the_save(monkeypatch):
    graph = real_mode(monkeypatch, full_state(fundamentals_report=""))
    monkeypatch.setattr(settings, "ANALYSTS", ["market", "news"])
    analyze()

    assert analyze()["fromCache"] is True
    assert len(graph.calls) == 1


def test_a_failed_second_stock_leaves_the_first_one_saved(monkeypatch):
    real_mode(monkeypatch, full_state())
    analyze()
    monkeypatch.setattr(agents, "_get_graph", lambda: FailingGraph())

    with pytest.raises(RuntimeError):
        agents.analyze_stock("MSFT", DAY, POSITIONS)

    assert agents.has_saved_result("AAPL", DAY, POSITIONS) is True
    assert agents.has_saved_result("MSFT", DAY, POSITIONS) is False


def test_a_fake_run_never_replaces_a_saved_real_result(monkeypatch):
    graph = real_mode(monkeypatch, full_state())
    real = analyze()

    monkeypatch.setattr(settings, "FAKE_AI", True)
    fake = analyze()
    assert fake["fromCache"] is False and fake["decision"].startswith("Rating:")

    monkeypatch.setattr(settings, "FAKE_AI", False)
    again = analyze()
    assert again["fromCache"] is True
    assert without_flag(again) == without_flag(real)
    assert len(graph.calls) == 1
