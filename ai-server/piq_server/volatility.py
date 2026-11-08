"""How much the portfolio's price moves, in plain Python (no AI).

Same rule as metrics.py: this number is calculated in code and the agents and
the summary model only receive it. Uses one year of daily closing prices per
stock (yfinance, already installed with TradingAgents) and the covariance of
the stocks' daily returns, so a portfolio spread over different stocks comes
out less volatile than the same money in one stock.
"""

from __future__ import annotations

import math

import numpy as np

from . import settings

TRADING_DAYS_PER_YEAR = 252
# Fewer daily returns than this is too little history to trust.
MIN_RETURN_DAYS = 30
# What fake mode (tests, trying the app with no key) reports.
FAKE_ANNUAL_VOLATILITY = 25.0
# yfinance's own default is 10 seconds per request. Set explicitly so a slow
# download can't hold up the (single) analysis job for long.
DOWNLOAD_TIMEOUT_SECONDS = 20
# Rounding can push a variance that is really 0 a hair below it; that is not a failure.
NEGATIVE_VARIANCE_TOLERANCE = 1e-12


def annual_portfolio_volatility(weights: dict[str, float],
                                closes: dict[str, list[float]]) -> float | None:
    """Annualised volatility of the portfolio, in percent, or None if it can't be worked out.

    weights: ticker -> share of the portfolio (any positive numbers; they are
        scaled to add up to 1).
    closes: ticker -> daily closing prices, oldest first, one price per day.
        Every stock must have a price for each of the same days.

    None means "no trustworthy answer": a stock has no prices, the stocks'
    histories don't line up, or there are fewer than MIN_RETURN_DAYS returns.
    """
    tickers = list(weights)
    if not tickers or any(t not in closes for t in tickers):
        return None

    lengths = {len(closes[t]) for t in tickers}
    if len(lengths) != 1 or lengths.pop() < MIN_RETURN_DAYS + 1:
        return None

    prices = np.array([closes[t] for t in tickers], dtype=float)  # one row per stock
    if not np.all(np.isfinite(prices)) or np.any(prices <= 0):
        return None

    returns = prices[:, 1:] / prices[:, :-1] - 1.0  # daily returns
    w = np.array([weights[t] for t in tickers], dtype=float)
    if np.any(w < 0) or w.sum() <= 0:
        return None
    w = w / w.sum()

    if len(tickers) == 1:
        daily_variance = float(np.var(returns[0], ddof=1))
    else:
        daily_variance = float(w @ np.cov(returns, ddof=1) @ w)
    if not math.isfinite(daily_variance):
        return None
    if daily_variance < 0:
        if daily_variance < -NEGATIVE_VARIANCE_TOLERANCE:
            return None
        daily_variance = 0.0
    return math.sqrt(daily_variance) * math.sqrt(TRADING_DAYS_PER_YEAR) * 100.0


def fetch_closes(tickers: list[str]) -> dict[str, list[float]]:
    """One year of daily closes per ticker from yfinance, over the days every stock traded."""
    import yfinance

    frame = yfinance.download(tickers, period="1y", interval="1d", auto_adjust=True,
                              progress=False, group_by="column",
                              timeout=DOWNLOAD_TIMEOUT_SECONDS)
    close = frame["Close"]
    if len(tickers) == 1 and not hasattr(close, "columns"):
        close = close.to_frame(name=tickers[0])
    close = close[tickers].dropna(how="any")  # keep only days when every stock has a price
    return {t: [float(x) for x in close[t].tolist()] for t in tickers}


def get_annual_volatility(weights: dict[str, float]) -> float | None:
    """The number jobs.py uses. Never raises: any problem means None (fallback in risk_score)."""
    if settings.FAKE_AI:
        return FAKE_ANNUAL_VOLATILITY
    try:
        closes = fetch_closes(list(weights))
        return annual_portfolio_volatility(weights, closes)
    except Exception as exc:  # no internet, unknown ticker, yfinance change, ...
        print(f"Could not work out price volatility ({type(exc).__name__}: {exc}). "
              "Using the default for the volatility part of the risk score.")
        return None
