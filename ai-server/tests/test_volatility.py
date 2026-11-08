import math
import sys
import types

import numpy as np
import pandas as pd
import pytest

from piq_server import settings, volatility
from piq_server.volatility import annual_portfolio_volatility, get_annual_volatility

DAYS = 60  # daily returns (a multiple of 4, used by the patterns below)
# +-1% a day with a mean of exactly 0: sample standard deviation, annualised (about 16%).
SINGLE_STOCK_VOLATILITY = 0.01 * math.sqrt(DAYS / (DAYS - 1)) * math.sqrt(252) * 100


def prices_from_returns(returns, start=100.0):
    prices = [start]
    for r in returns:
        prices.append(prices[-1] * (1 + r))
    return prices


def alternating(n=DAYS):
    """+1%, -1%, +1%, -1% ..."""
    return [0.01 if i % 2 == 0 else -0.01 for i in range(n)]


def paired(n=DAYS):
    """+1%, +1%, -1%, -1% ... exactly uncorrelated with alternating()."""
    return [0.01 if (i // 2) % 2 == 0 else -0.01 for i in range(n)]


def install_fake_yfinance(monkeypatch, frame):
    """Replaces the yfinance module with one whose download() returns `frame`."""
    module = types.ModuleType("yfinance")
    module.calls = []

    def download(tickers, **kwargs):
        module.calls.append({"tickers": tickers, **kwargs})
        return frame

    module.download = download
    monkeypatch.setitem(sys.modules, "yfinance", module)
    return module


def close_frame(close_by_ticker):
    """Shaped like yfinance's result: columns are (Close, ticker), one row per day."""
    columns = pd.MultiIndex.from_product([["Close"], list(close_by_ticker)])
    return pd.DataFrame(list(zip(*close_by_ticker.values())), columns=columns)


# ---- the maths ----

def test_one_stock_volatility_is_annualised_daily_swings():
    closes = {"AAPL": prices_from_returns(alternating())}
    result = annual_portfolio_volatility({"AAPL": 100}, closes)
    assert result == pytest.approx(SINGLE_STOCK_VOLATILITY, rel=1e-9)


def test_two_identical_stocks_are_no_safer_than_one():
    series = prices_from_returns(alternating())
    result = annual_portfolio_volatility({"A": 50, "B": 50}, {"A": series, "B": series})
    one = annual_portfolio_volatility({"A": 100}, {"A": series})
    assert result == pytest.approx(one)


def test_spreading_over_unrelated_stocks_lowers_volatility():
    closes = {"A": prices_from_returns(alternating()), "B": prices_from_returns(paired())}
    spread = annual_portfolio_volatility({"A": 50, "B": 50}, closes)
    one = annual_portfolio_volatility({"A": 100}, {"A": closes["A"]})
    # two uncorrelated stocks of equal swings: volatility / sqrt(2)
    assert spread < one
    assert spread == pytest.approx(one / math.sqrt(2), rel=1e-6)


def test_unequal_weights_change_the_result():
    closes = {"A": prices_from_returns(alternating()), "B": prices_from_returns(paired())}
    one = annual_portfolio_volatility({"A": 100}, {"A": closes["A"]})
    even = annual_portfolio_volatility({"A": 50, "B": 50}, closes)
    lopsided = annual_portfolio_volatility({"A": 75, "B": 25}, closes)
    # uncorrelated stocks of equal swings: volatility = one x sqrt(w1^2 + w2^2)
    assert lopsided == pytest.approx(one * math.sqrt(0.75 ** 2 + 0.25 ** 2), rel=1e-6)
    assert lopsided > even


def test_weights_only_matter_relative_to_each_other():
    closes = {"A": prices_from_returns(alternating()), "B": prices_from_returns(paired())}
    assert annual_portfolio_volatility({"A": 2, "B": 2}, closes) == pytest.approx(
        annual_portfolio_volatility({"A": 50, "B": 50}, closes))


def test_thirty_returns_are_enough_and_twenty_nine_are_not():
    enough = {"AAPL": prices_from_returns(alternating(30))}  # 31 prices, 30 returns
    too_few = {"AAPL": prices_from_returns(alternating(29))}  # 30 prices, 29 returns
    assert annual_portfolio_volatility({"AAPL": 100}, enough) is not None
    assert annual_portfolio_volatility({"AAPL": 100}, too_few) is None


def test_too_little_history_gives_none():
    closes = {"AAPL": prices_from_returns(alternating(20))}  # 21 prices, 20 returns
    assert annual_portfolio_volatility({"AAPL": 100}, closes) is None


def test_stock_with_no_prices_gives_none():
    closes = {"A": prices_from_returns(alternating())}
    assert annual_portfolio_volatility({"A": 50, "B": 50}, closes) is None


def test_histories_that_do_not_line_up_give_none():
    closes = {"A": prices_from_returns(alternating()), "B": prices_from_returns(paired(DAYS - 4))}
    assert annual_portfolio_volatility({"A": 50, "B": 50}, closes) is None


def test_bad_prices_give_none():
    good = prices_from_returns(alternating())
    assert annual_portfolio_volatility({"A": 100}, {"A": good[:-1] + [float("nan")]}) is None
    assert annual_portfolio_volatility({"A": 100}, {"A": good[:-1] + [0.0]}) is None


def test_no_holdings_gives_none():
    assert annual_portfolio_volatility({}, {}) is None


def test_tiny_negative_variance_from_rounding_counts_as_zero(monkeypatch):
    closes = {"A": prices_from_returns(alternating()), "B": prices_from_returns(paired())}
    monkeypatch.setattr(volatility.np, "cov",
                        lambda *args, **kwargs: np.array([[-1e-15, 0.0], [0.0, -1e-15]]))
    assert annual_portfolio_volatility({"A": 50, "B": 50}, closes) == 0.0


def test_clearly_negative_variance_is_rejected(monkeypatch):
    closes = {"A": prices_from_returns(alternating()), "B": prices_from_returns(paired())}
    monkeypatch.setattr(volatility.np, "cov",
                        lambda *args, **kwargs: np.array([[-1e-6, 0.0], [0.0, -1e-6]]))
    assert annual_portfolio_volatility({"A": 50, "B": 50}, closes) is None


# ---- the number jobs.py asks for ----

def test_fake_mode_returns_a_fixed_25_percent():
    assert settings.FAKE_AI  # tests/conftest.py turns fake mode on
    assert get_annual_volatility({"AAPL": 100}) == 25.0


def test_real_mode_uses_the_downloaded_prices(monkeypatch):
    monkeypatch.setattr(settings, "FAKE_AI", False)
    seen = {}

    def fake_fetch(tickers):
        seen["tickers"] = tickers
        return {"AAPL": prices_from_returns(alternating())}

    monkeypatch.setattr(volatility, "fetch_closes", fake_fetch)
    result = get_annual_volatility({"AAPL": 100})
    assert seen["tickers"] == ["AAPL"]
    assert result == pytest.approx(SINGLE_STOCK_VOLATILITY, rel=1e-9)


def test_download_failure_gives_none_instead_of_crashing(monkeypatch):
    monkeypatch.setattr(settings, "FAKE_AI", False)

    def broken_fetch(tickers):
        raise ConnectionError("no internet")

    monkeypatch.setattr(volatility, "fetch_closes", broken_fetch)
    assert get_annual_volatility({"AAPL": 100}) is None


# ---- fetch_closes, against a fake yfinance (no network) ----

def test_fetch_closes_reads_a_one_ticker_frame_with_two_column_levels(monkeypatch):
    fake = install_fake_yfinance(monkeypatch, close_frame({"AAPL": [1.0, 2.0, 3.0]}))
    assert volatility.fetch_closes(["AAPL"]) == {"AAPL": [1.0, 2.0, 3.0]}
    call = fake.calls[0]
    assert call["tickers"] == ["AAPL"]
    assert call["period"] == "1y" and call["interval"] == "1d"
    assert call["timeout"] == volatility.DOWNLOAD_TIMEOUT_SECONDS


def test_fetch_closes_reads_a_plain_close_column(monkeypatch):
    frame = pd.DataFrame({"Close": [1.0, 2.0, 3.0], "Open": [9.0, 9.0, 9.0]})
    install_fake_yfinance(monkeypatch, frame)
    assert volatility.fetch_closes(["AAPL"]) == {"AAPL": [1.0, 2.0, 3.0]}


def test_fetch_closes_drops_days_a_stock_has_no_price_and_keeps_requested_order(monkeypatch):
    frame = close_frame({"MSFT": [10.0, 20.0, 30.0, 40.0],
                         "AAPL": [1.0, float("nan"), 3.0, 4.0]})
    install_fake_yfinance(monkeypatch, frame)
    result = volatility.fetch_closes(["AAPL", "MSFT"])
    assert result == {"AAPL": [1.0, 3.0, 4.0], "MSFT": [10.0, 30.0, 40.0]}
    assert list(result) == ["AAPL", "MSFT"]


def test_a_ticker_missing_from_the_download_means_no_volatility(monkeypatch):
    monkeypatch.setattr(settings, "FAKE_AI", False)
    install_fake_yfinance(monkeypatch, close_frame({"AAPL": [1.0, 2.0, 3.0]}))
    with pytest.raises(KeyError):
        volatility.fetch_closes(["AAPL", "MSFT"])
    assert get_annual_volatility({"AAPL": 50, "MSFT": 50}) is None


def test_whole_real_mode_path_with_a_fake_download(monkeypatch):
    monkeypatch.setattr(settings, "FAKE_AI", False)
    series = prices_from_returns(alternating())
    install_fake_yfinance(monkeypatch, close_frame({"AAPL": series}))
    assert get_annual_volatility({"AAPL": 100}) == pytest.approx(SINGLE_STOCK_VOLATILITY, rel=1e-9)
