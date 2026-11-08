"""Portfolio numbers, computed in plain Python (no AI).

Same rule as the Android app (CLAUDE.md): every number shown to the user is
calculated in code. The AI agents and the summary model only receive these
numbers and explain them; they never calculate them.
"""

from __future__ import annotations

from dataclasses import dataclass

# How risky each TradingAgents rating is, on a 0-100 scale. "REVIEW" means
# the agents' final decision could not be read as one of the five ratings.
# No rating is 0: no stock has zero risk, even one the agents like.
RATING_RISK = {
    "Buy": 15.0,
    "Overweight": 30.0,
    "Hold": 50.0,
    "Underweight": 70.0,
    "Sell": 85.0,
    "REVIEW": 50.0,
}

# How much of the final risk score each part carries (they add up to 1).
VOLATILITY_WEIGHT = 0.4
CONCENTRATION_WEIGHT = 0.3
RATINGS_WEIGHT = 0.3

# The risk score never goes below this, and the volatility part is this when
# the price history could not be downloaded.
MIN_RISK = 5.0
VOLATILITY_PART_FALLBACK = 50.0


@dataclass(frozen=True)
class Holding:
    ticker: str
    quantity: float
    purchase_price: float
    current_price: float

    @property
    def value(self) -> float:
        return self.quantity * self.current_price

    @property
    def cost(self) -> float:
        return self.quantity * self.purchase_price


def round2(x: float) -> float:
    return round(x + 0.0, 2)


def portfolio_metrics(holdings: list[Holding]) -> dict:
    """Totals, allocation, concentration and diversification for the portfolio."""
    total_value = sum(h.value for h in holdings)
    total_cost = sum(h.cost for h in holdings)
    if total_value <= 0:
        raise ValueError("portfolio has no value")

    positions = []
    for h in holdings:
        weight = h.value / total_value
        pl = h.value - h.cost
        positions.append({
            "ticker": h.ticker,
            "value": round2(h.value),
            "allocationPercent": round2(weight * 100),
            "profitLoss": round2(pl),
            "profitLossPercent": round2(pl / h.cost * 100) if h.cost > 0 else 0.0,
        })
    positions.sort(key=lambda p: p["value"], reverse=True)

    weights = [h.value / total_value for h in holdings]
    # Herfindahl-Hirschman Index: sum of squared weights. 1.0 = everything in
    # one stock; 1/n = spread evenly over n stocks.
    hhi = sum(w * w for w in weights)
    top3 = sum(sorted(weights, reverse=True)[:3])

    return {
        "totalValue": round2(total_value),
        "totalCost": round2(total_cost),
        "totalProfitLoss": round2(total_value - total_cost),
        "totalProfitLossPercent": round2((total_value - total_cost) / total_cost * 100) if total_cost > 0 else 0.0,
        "numberOfHoldings": len(holdings),
        "largestPosition": positions[0]["ticker"],
        "largestPositionPercent": positions[0]["allocationPercent"],
        "top3Percent": round2(top3 * 100),
        "concentrationIndex": round(hhi, 4),
        # 0 = all in one stock, close to 100 = spread over many equal positions.
        "diversificationScore": round2((1 - hhi) * 100),
        "positions": positions,
    }


def risk_score(metrics: dict, ratings: dict[str, str],
               annual_volatility: float | None = None) -> dict:
    """A 0-100 risk percentage from three parts, never below MIN_RISK.

    volatility part    = annual volatility % x 2, capped at 100 (25% -> 50);
                         VOLATILITY_PART_FALLBACK when annual_volatility is
                         None (the price history could not be downloaded)
    concentration part = concentrationIndex x 100 (one stock = 100)
    ratings part       = allocation-weighted average of RATING_RISK

    risk = 0.4 x volatility part + 0.3 x concentration part + 0.3 x ratings part
    """
    volatility_available = annual_volatility is not None
    volatility_part = (max(0.0, min(100.0, annual_volatility * 2)) if volatility_available
                       else VOLATILITY_PART_FALLBACK)
    concentration_part = metrics["concentrationIndex"] * 100
    ratings_part = sum(
        p["allocationPercent"] / 100 * RATING_RISK.get(ratings.get(p["ticker"], "REVIEW"), 50.0)
        for p in metrics["positions"]
    )
    score = max(MIN_RISK,
                VOLATILITY_WEIGHT * volatility_part
                + CONCENTRATION_WEIGHT * concentration_part
                + RATINGS_WEIGHT * ratings_part)
    level = "Low" if score < 35 else "Medium" if score < 65 else "High"
    return {
        "riskPercent": round2(score),
        "riskLevel": level,
        "volatilityPart": round2(volatility_part),
        "concentrationPart": round2(concentration_part),
        "ratingsPart": round2(ratings_part),
        # None (JSON null) when unavailable, so it is never mistaken for "no volatility".
        "annualVolatilityPercent": round2(annual_volatility) if volatility_available else None,
        "volatilityAvailable": volatility_available,
    }
