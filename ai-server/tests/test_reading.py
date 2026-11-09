import pytest

from piq_server import reading
from piq_server.reading import NEWS_SUMMARY_CHARS, sources_for, stock_reading

ALL = ("market", "news", "fundamentals")
FULL = {"market": "m", "news": "n", "fundamentals": "f"}
DEFAULT_SOURCES = ["Yahoo Finance prices and indicators",
                   "Yahoo Finance news, past 7 days",
                   "SEC EDGAR financial statements"]


# ---- sources_for ----

def test_default_sources_for_all_three_analysts():
    assert sources_for(FULL, ALL) == DEFAULT_SOURCES


def test_an_analyst_that_is_switched_off_has_no_source():
    assert sources_for(FULL, ("market", "news")) == DEFAULT_SOURCES[:2]
    assert sources_for(FULL, ("fundamentals",)) == DEFAULT_SOURCES[2:]
    assert sources_for(FULL, ()) == []


def test_an_analyst_with_an_empty_report_has_no_source():
    reports = {"market": "m", "news": "   ", "fundamentals": ""}
    assert sources_for(reports, ALL) == DEFAULT_SOURCES[:1]
    assert sources_for({"market": "m"}, ALL) == DEFAULT_SOURCES[:1]  # a missing report counts as empty
    assert sources_for({}, ALL) == []


def test_a_report_given_as_content_parts_counts_when_it_has_text():
    reports = {"market": [{"type": "text", "text": "m"}], "news": [{"type": "text", "text": " "}],
               "fundamentals": []}
    assert sources_for(reports, ALL) == DEFAULT_SOURCES[:1]


def test_analysts_that_are_not_part_of_the_reports_are_ignored():
    assert sources_for(FULL, ALL + ("social",)) == DEFAULT_SOURCES


def test_the_graphs_own_vendors_are_used():
    vendors = {"core_stock_apis": "alpha_vantage", "technical_indicators": "yfinance",
               "news_data": "alpha_vantage", "fundamental_data": "yfinance"}
    assert sources_for(FULL, ALL, vendors) == [
        "Alpha Vantage prices, Yahoo Finance indicators",
        "Alpha Vantage news, past 7 days",
        "Yahoo Finance financial statements",
    ]


def test_only_the_first_vendor_of_a_chain_is_named():
    assert sources_for(FULL, ALL, {"fundamental_data": "sec_edgar,yfinance"})[2] == \
        "SEC EDGAR financial statements"
    assert sources_for(FULL, ALL, {"fundamental_data": " yfinance , sec_edgar "})[2] == \
        "Yahoo Finance financial statements"


def test_a_category_the_config_does_not_name_uses_the_default():
    assert sources_for(FULL, ALL, {"news_data": "alpha_vantage"}) == [
        "Yahoo Finance prices and indicators",
        "Alpha Vantage news, past 7 days",
        "SEC EDGAR financial statements",
    ]
    assert sources_for(FULL, ALL, {}) == DEFAULT_SOURCES
    assert sources_for(FULL, ALL, None) == DEFAULT_SOURCES


def test_the_word_default_and_empty_values_fall_back_to_the_usual_vendor():
    vendors = {"news_data": "default", "fundamental_data": "", "core_stock_apis": None}
    assert sources_for(FULL, ALL, vendors) == DEFAULT_SOURCES


def test_an_unknown_vendor_is_shown_by_its_own_name():
    assert sources_for(FULL, ALL, {"news_data": "acme_news"})[1] == "acme_news news, past 7 days"


# ---- stock_reading ----

def test_the_news_summary_is_plain_text_from_the_news_report():
    reports = dict(FULL, news="## Headline\n\n- **Shares** rose on [strong demand](https://x.example).")
    result = stock_reading(reports, ALL)
    assert result["newsSummary"] == "Headline\nShares rose on strong demand."
    assert result["sources"] == DEFAULT_SOURCES


def test_the_news_summary_is_at_most_700_characters_and_ends_with_dots_when_cut():
    assert NEWS_SUMMARY_CHARS == 700
    long_report = "## Headline\n\n" + "Shares rose on strong demand and costs fell.\n" * 100
    summary = stock_reading(dict(FULL, news=long_report), ALL)["newsSummary"]
    assert 0 < len(summary) <= 700
    assert summary.endswith("...")
    assert summary.startswith("Headline\nShares rose")
    assert "#" not in summary


def test_a_short_news_report_is_kept_whole_without_dots():
    summary = stock_reading(dict(FULL, news="Quiet week."), ALL)["newsSummary"]
    assert summary == "Quiet week."


def test_no_news_report_gives_an_empty_summary_and_no_news_source():
    result = stock_reading({"market": "m", "news": "", "fundamentals": "f"}, ALL)
    assert result["newsSummary"] == ""
    assert result["sources"] == [DEFAULT_SOURCES[0], DEFAULT_SOURCES[2]]
    assert stock_reading({}, ALL) == {"newsSummary": "", "sources": []}


def test_the_news_summary_is_read_from_content_parts():
    reports = dict(FULL, news=[{"type": "text", "text": "## Big news\n\nShares rose."}])
    assert stock_reading(reports, ALL)["newsSummary"] == "Big news\nShares rose."


def test_the_default_vendors_match_trading_agents_own_defaults():
    default_config = pytest.importorskip("tradingagents.default_config")
    real = default_config.build_default_config()["data_vendors"]
    for category, vendor in reading.DEFAULT_DATA_VENDORS.items():
        assert real[category] == vendor
