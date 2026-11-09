"""What the agents read for each stock (Issue C), in plain Python (no AI).

Two things are shown to the user: the start of the News Analyst's report, and
the data sources the analysts can use. The sources come from TradingAgents'
configured data vendors. They are not a log of the exact calls the model made:
TradingAgents runs each analyst in a private sub-graph and keeps its tool calls
out of the final state, so only "this analyst produced a report, and these are
the sources it can use" can be said honestly. In particular:
- only the first vendor of a chain is named, so "SEC EDGAR financial statements"
  is what is asked first; a non-US company, or an EDGAR failure, falls back to
  Yahoo Finance;
- "past 7 days" is what the News Analyst is told ("the past week"); the model
  picks the exact dates;
- a source is listed when its analyst wrote a report, even if every tool call
  came back with no data;
- the News Analyst's optional tools (FRED macro data, which needs a key, and
  Polymarket odds) are not listed, so the list is not exhaustive;
- per-tool overrides in TradingAgents' config (tool_vendors) are not followed.
"""

from __future__ import annotations

from .text import as_text, plain_text, shorten

NEWS_SUMMARY_CHARS = 700
# The News Analyst is told to look at "the past week".
NEWS_LOOKBACK_DAYS = 7

# TradingAgents' default data vendors at the commit pinned in requirements.txt
# (default_config.py, "data_vendors"). Used in fake mode, and for a category the
# graph's own config does not name, so fake mode never has to import TradingAgents.
DEFAULT_DATA_VENDORS = {
    "core_stock_apis": "yfinance",
    "technical_indicators": "yfinance",
    "fundamental_data": "sec_edgar,yfinance",
    "news_data": "yfinance",
}
VENDOR_NAMES = {
    "yfinance": "Yahoo Finance",
    "sec_edgar": "SEC EDGAR",
    "alpha_vantage": "Alpha Vantage",
}


def _vendor(vendors: dict | None, category: str) -> str:
    """The first vendor in a category's chain (the one asked first), as a readable name.

    "default" (TradingAgents: "all available vendors") or a missing value is shown
    as the usual vendor for the category, which is an assumption, not a lookup.
    """
    first = str((vendors or {}).get(category) or "").split(",")[0].strip()
    if not first or first == "default":  # "default" means "all available": name the usual one
        first = DEFAULT_DATA_VENDORS[category].split(",")[0]
    return VENDOR_NAMES.get(first, first)


def sources_for(reports: dict, enabled_analysts, vendors: dict | None = None) -> list[str]:
    """The data source of each analyst that is switched on and produced a report.

    reports: analyst name ("market", "news", "fundamentals") -> report text.
    vendors: TradingAgents' data_vendors config, or None for its defaults.
    """
    def produced(analyst: str) -> bool:
        return analyst in enabled_analysts and bool(as_text(reports.get(analyst)).strip())

    sources = []
    if produced("market"):
        prices = _vendor(vendors, "core_stock_apis")
        indicators = _vendor(vendors, "technical_indicators")
        sources.append(f"{prices} prices and indicators" if prices == indicators
                       else f"{prices} prices, {indicators} indicators")
    if produced("news"):
        sources.append(f"{_vendor(vendors, 'news_data')} news, past {NEWS_LOOKBACK_DAYS} days")
    if produced("fundamentals"):
        sources.append(f"{_vendor(vendors, 'fundamental_data')} financial statements")
    return sources


def stock_reading(reports: dict, enabled_analysts, vendors: dict | None = None) -> dict:
    """{"newsSummary", "sources"} for one stock.

    newsSummary is the start of the News Analyst's report as plain text, at most
    NEWS_SUMMARY_CHARS characters, cut at a whole word (empty if it wrote none).
    """
    return {
        "newsSummary": shorten(plain_text(reports.get("news")), NEWS_SUMMARY_CHARS),
        "sources": sources_for(reports, enabled_analysts, vendors),
    }
