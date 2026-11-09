import pytest

from piq_server import confidence as confidence_module
from piq_server.confidence import (
    clamp_confidence,
    level_for,
    normalise_rating,
    parse_research_vote,
    parse_trader_vote,
    portfolio_confidence,
    stock_confidence,
)

ALL = ("market", "news", "fundamentals")
FULL = {"market": "m", "news": "n", "fundamentals": "f"}


def plan(rating):
    return f"**Recommendation**: {rating}\n\n**Rationale**: reasons\n\n**Strategic Actions**: steps"


def action(name):
    return f"**Action**: {name}\n\n**Reasoning**: reasons\n\nFINAL TRANSACTION PROPOSAL: **{name.upper()}**"


def score(final, research, trader, reports=FULL, analysts=ALL):
    return stock_confidence(final, plan(research), action(trader), reports, analysts)


# ---- agreement between the three votes ----

def test_all_three_agree_is_90_and_high():
    result = score("Buy", "Buy", "Buy")
    assert result["confidencePercent"] == 90
    assert result["confidenceLevel"] == "High"
    assert result["votes"] == {"researchManager": "Buy", "trader": "Buy", "final": "Buy"}


def test_two_of_three_is_65_and_medium():
    for result in (score("Buy", "Buy", "Hold"), score("Buy", "Sell", "Buy")):
        assert result["confidencePercent"] == 65
        assert result["confidenceLevel"] == "Medium"


def test_one_of_three_is_40_and_low():
    result = score("Buy", "Sell", "Sell")
    assert result["confidencePercent"] == 40
    assert result["confidenceLevel"] == "Low"


def test_overweight_points_the_same_way_as_buy():
    assert score("Buy", "Overweight", "Buy")["confidencePercent"] == 90
    assert score("Overweight", "Buy", "Buy")["confidencePercent"] == 90


def test_underweight_and_sell_point_the_same_way():
    assert score("Sell", "Underweight", "Sell")["confidencePercent"] == 90
    assert score("Underweight", "Sell", "Sell")["confidencePercent"] == 90


def test_hold_only_agrees_with_hold():
    assert score("Hold", "Hold", "Hold")["confidencePercent"] == 90
    assert score("Hold", "Buy", "Hold")["confidencePercent"] == 65
    assert score("Hold", "Buy", "Sell")["confidencePercent"] == 40


def test_unreadable_votes_count_as_not_agreeing():
    result = stock_confidence("Buy", "no recommendation line here", "", FULL, ALL)
    assert result["confidencePercent"] == 40
    assert result["votes"] == {"researchManager": None, "trader": None, "final": "Buy"}
    assert stock_confidence("Buy", None, None, FULL, ALL)["confidencePercent"] == 40


def test_one_unreadable_vote_leaves_two_of_three():
    result = stock_confidence("Buy", plan("Buy"), "", FULL, ALL)
    assert result["confidencePercent"] == 65
    assert result["votes"]["trader"] is None


def test_unreadable_final_rating_scores_one_of_three():
    result = stock_confidence("REVIEW", plan("Buy"), action("Buy"), FULL, ALL)
    assert result["confidencePercent"] == 40
    assert result["votes"] == {"researchManager": "Buy", "trader": "Buy", "final": None}


def test_final_rating_is_read_ignoring_case_and_spaces():
    result = stock_confidence(" buy ", plan("Buy"), action("Buy"), FULL, ALL)
    assert result["votes"]["final"] == "Buy"
    assert result["confidencePercent"] == 90


# ---- empty analyst reports ----

def test_each_empty_report_costs_ten_points():
    one_empty = dict(FULL, fundamentals="")
    two_empty = dict(FULL, fundamentals="", news="   ")  # blank counts as empty
    three_empty = {"market": "", "news": "", "fundamentals": ""}
    assert score("Buy", "Buy", "Buy", one_empty)["confidencePercent"] == 80
    assert score("Buy", "Buy", "Buy", one_empty)["confidenceLevel"] == "High"
    assert score("Buy", "Buy", "Buy", two_empty)["confidencePercent"] == 70
    assert score("Buy", "Buy", "Buy", two_empty)["confidenceLevel"] == "Medium"
    assert score("Buy", "Buy", "Buy", three_empty)["confidencePercent"] == 60


def test_a_missing_report_counts_as_empty():
    assert score("Buy", "Buy", "Buy", {"market": "m"})["confidencePercent"] == 70


def test_a_report_given_as_content_parts_is_empty_only_when_all_its_text_is():
    parts = lambda text: [{"type": "text", "text": text}]  # noqa: E731
    reports = {"market": parts("market text"), "news": parts("  "), "fundamentals": []}
    assert score("Buy", "Buy", "Buy", reports)["confidencePercent"] == 70  # news and fundamentals empty


def test_the_lowest_possible_score_is_exactly_ten():
    result = score("Buy", "Sell", "Sell", {})  # 40 - 3 x 10
    assert result["confidencePercent"] == 10
    assert result["confidenceLevel"] == "Low"


def test_the_clamp_lifts_a_score_that_would_fall_below_ten(monkeypatch):
    monkeypatch.setattr(confidence_module, "EMPTY_REPORT_PENALTY", 100.0)
    assert score("Buy", "Buy", "Buy", {})["confidencePercent"] == 10


def test_the_clamp_lowers_a_score_that_would_go_above_95(monkeypatch):
    monkeypatch.setitem(confidence_module.AGREEMENT_SCORE, 3, 200.0)
    assert score("Buy", "Buy", "Buy")["confidencePercent"] == 95


def test_only_analysts_that_were_switched_on_are_checked():
    reports = {"market": "m", "news": "", "fundamentals": ""}
    assert score("Buy", "Buy", "Buy", reports, ("market", "news"))["confidencePercent"] == 80
    assert score("Buy", "Buy", "Buy", reports, ("market",))["confidencePercent"] == 90


def test_an_analyst_whose_report_is_not_kept_is_ignored():
    assert score("Buy", "Buy", "Buy", FULL, ALL + ("social",))["confidencePercent"] == 90


# ---- clamp and levels ----

def test_clamp_keeps_confidence_between_10_and_95():
    assert clamp_confidence(5) == 10
    assert clamp_confidence(120) == 95
    assert clamp_confidence(50) == 50


def test_levels_at_the_boundaries():
    assert level_for(95) == "High"
    assert level_for(75) == "High"
    assert level_for(74.99) == "Medium"
    assert level_for(50) == "Medium"
    assert level_for(49.99) == "Low"
    assert level_for(10) == "Low"


# ---- reading the votes out of TradingAgents' text ----

def test_votes_are_read_from_trading_agents_own_output():
    schemas = pytest.importorskip("tradingagents.agents.schemas")
    research_text = schemas.render_research_plan(schemas.ResearchPlan(
        recommendation="Overweight", rationale="r", strategic_actions="s"))
    trader_text = schemas.render_trader_proposal(schemas.TraderProposal(action="Sell", reasoning="x"))

    assert parse_research_vote(research_text) == "Overweight"
    assert parse_trader_vote(trader_text) == "Sell"


def test_research_vote_is_read_from_the_recommendation_line():
    assert parse_research_vote("**Recommendation**: Overweight\n\n**Rationale**: x") == "Overweight"
    assert parse_research_vote("recommendation: hold") == "Hold"
    assert parse_research_vote("**Recommendation**: **Sell**") == "Sell"
    assert parse_research_vote("**Recommendation**: Buy ... **Recommendation**: Sell") == "Buy"


def test_research_vote_is_none_when_it_cannot_be_read():
    for text in (None, "", "no such line", "**Recommendation**: Maybe", "**Recommendation**: Buyer"):
        assert parse_research_vote(text) is None


def test_trader_vote_is_read_from_the_action_line():
    assert parse_trader_vote("**Action**: Sell\n\n**Reasoning**: x") == "Sell"
    assert parse_trader_vote("action: hold") == "Hold"


def test_trader_vote_is_none_when_it_cannot_be_read():
    for text in (None, "", "**Action**: Overweight",  # the trader only says Buy, Hold or Sell
                 "**Strategic Actions**: Buy", "FINAL TRANSACTION PROPOSAL: **BUY**"):
        assert parse_trader_vote(text) is None


def test_free_text_layouts_a_model_may_use_are_read():
    assert parse_trader_vote("**Action:** Buy\n\n**Reasoning**: x") == "Buy"  # colon inside the bold
    assert parse_trader_vote("- **Action**: Hold") == "Hold"
    assert parse_trader_vote("* **Action**: Sell") == "Sell"
    assert parse_trader_vote("> **Action**: Buy") == "Buy"
    assert parse_trader_vote("Intro line\n   Action : Hold") == "Hold"
    assert parse_research_vote("1. **Recommendation**: Underweight") == "Underweight"
    assert parse_research_vote("## Recommendation: Buy") == "Buy"


def test_a_label_in_the_middle_of_a_sentence_is_not_a_vote():
    assert parse_trader_vote("we recommend action: sell") is None
    assert parse_trader_vote("The recommended action: sell is wrong.\n**Action**: Buy") == "Buy"
    assert parse_research_vote("My recommendation: sell, said the intern.\n**Recommendation**: Hold") == "Hold"


def test_the_value_must_be_on_the_same_line_as_the_label():
    assert parse_trader_vote("**Action**:\nHold on a moment") is None


def test_a_reply_given_as_content_parts_is_read():
    assert parse_trader_vote([{"type": "text", "text": "**Action**: Hold\n"}]) == "Hold"
    assert parse_research_vote(["**Recommendation**: Buy"]) == "Buy"
    assert parse_trader_vote([{"type": "image"}]) is None
    assert parse_trader_vote([]) is None


def test_normalise_rating():
    assert normalise_rating("sell") == "Sell"
    assert normalise_rating("REVIEW") is None
    assert normalise_rating(None) is None


# ---- the whole portfolio ----

def stocks_with(**percents):
    return [{"ticker": t, "confidencePercent": p} for t, p in percents.items()]


def test_portfolio_confidence_is_weighted_by_position():
    # (75 x 90 + 25 x 40) / 100 = 77.5, rounded half up to a whole number
    result = portfolio_confidence(stocks_with(AAPL=90.0, MSFT=40.0), {"AAPL": 75, "MSFT": 25})
    assert result == {"confidencePercent": 78.0, "confidenceLevel": "High"}


def test_portfolio_weights_only_matter_relative_to_each_other():
    assert portfolio_confidence(stocks_with(A=90.0, B=40.0), {"A": 3, "B": 1}) == \
        portfolio_confidence(stocks_with(A=90.0, B=40.0), {"A": 75, "B": 25})
    # (3 x 40 + 1 x 90) / 4 = 52.5
    assert portfolio_confidence(stocks_with(A=40.0, B=90.0), {"A": 3, "B": 1}) == \
        {"confidencePercent": 53.0, "confidenceLevel": "Medium"}


def test_the_portfolio_percent_is_whole_and_its_level_matches_it():
    # 74.6 is shown as 75, which is High; 74.4 is shown as 74, which is Medium
    assert portfolio_confidence(stocks_with(A=90.0, B=65.0), {"A": 384, "B": 616}) == \
        {"confidencePercent": 75.0, "confidenceLevel": "High"}
    assert portfolio_confidence(stocks_with(A=90.0, B=65.0), {"A": 376, "B": 624}) == \
        {"confidencePercent": 74.0, "confidenceLevel": "Medium"}
    # 49.6 is shown as 50, which is Medium; 49.4 is shown as 49, which is Low
    assert portfolio_confidence(stocks_with(A=65.0, B=40.0), {"A": 384, "B": 616}) == \
        {"confidencePercent": 50.0, "confidenceLevel": "Medium"}
    assert portfolio_confidence(stocks_with(A=65.0, B=40.0), {"A": 376, "B": 624}) == \
        {"confidencePercent": 49.0, "confidenceLevel": "Low"}


def test_one_stock_portfolio_has_that_stocks_confidence():
    assert portfolio_confidence(stocks_with(AAPL=65.0), {"AAPL": 100}) == \
        {"confidencePercent": 65.0, "confidenceLevel": "Medium"}


def test_a_stock_with_no_weight_does_not_count():
    assert portfolio_confidence(stocks_with(A=90.0, B=40.0), {"A": 10})["confidencePercent"] == 90


def test_portfolio_confidence_is_none_when_there_is_nothing_to_average():
    assert portfolio_confidence([], {}) is None
    assert portfolio_confidence(stocks_with(A=90.0), {"A": 0}) is None
    assert portfolio_confidence([{"ticker": "A"}], {"A": 100}) is None
