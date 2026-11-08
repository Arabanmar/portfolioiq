import pytest

from piq_server.metrics import RATING_RISK, Holding, portfolio_metrics, risk_score


def test_single_stock_is_fully_concentrated():
    m = portfolio_metrics([Holding("AAPL", 10, 150, 200)])
    assert m["totalValue"] == 2000
    assert m["totalCost"] == 1500
    assert m["totalProfitLoss"] == 500
    assert m["totalProfitLossPercent"] == pytest.approx(33.33)
    assert m["largestPositionPercent"] == 100
    assert m["concentrationIndex"] == 1
    assert m["diversificationScore"] == 0


def test_allocation_and_diversification_for_two_stocks():
    # 3000 + 1000 = 4000 -> 75% / 25%; HHI = 0.5625 + 0.0625 = 0.625
    m = portfolio_metrics([Holding("MSFT", 10, 250, 300), Holding("AAPL", 5, 200, 200)])
    assert m["largestPosition"] == "MSFT"
    assert [p["allocationPercent"] for p in m["positions"]] == [75, 25]
    assert m["concentrationIndex"] == 0.625
    assert m["diversificationScore"] == 37.5
    assert m["top3Percent"] == 100


def test_ten_equal_stocks_are_well_diversified():
    m = portfolio_metrics([Holding(f"T{i}", 1, 100, 100) for i in range(10)])
    assert m["diversificationScore"] == 90
    assert m["top3Percent"] == 30


def equal_holdings(n):
    return [Holding(f"T{i}", 1, 100, 100) for i in range(n)]


def test_rating_risk_values_and_none_is_zero():
    assert RATING_RISK == {"Buy": 15, "Overweight": 30, "Hold": 50,
                           "Underweight": 70, "Sell": 85, "REVIEW": 50}
    assert all(value > 0 for value in RATING_RISK.values())


def test_risk_mixes_volatility_concentration_and_ratings():
    m = portfolio_metrics([Holding("MSFT", 10, 250, 300), Holding("AAPL", 5, 200, 200)])
    r = risk_score(m, {"MSFT": "Buy", "AAPL": "Sell"}, annual_volatility=25)
    # volatility 25% x 2 = 50; concentration 62.5; ratings 0.75*15 + 0.25*85 = 32.5
    assert r["volatilityPart"] == 50
    assert r["concentrationPart"] == 62.5
    assert r["ratingsPart"] == 32.5
    # 0.4*50 + 0.3*62.5 + 0.3*32.5 = 20 + 18.75 + 9.75
    assert r["riskPercent"] == pytest.approx(48.5)
    assert r["riskLevel"] == "Medium"
    assert r["annualVolatilityPercent"] == 25
    assert r["volatilityAvailable"] is True


def test_single_buy_stock_is_not_zero_risk():
    m = portfolio_metrics([Holding("AAPL", 10, 150, 200)])
    r = risk_score(m, {"AAPL": "Buy"}, annual_volatility=25)
    # 0.4*50 + 0.3*100 + 0.3*15 = 20 + 30 + 4.5
    assert r["riskPercent"] == pytest.approx(54.5)
    assert r["riskPercent"] > 0


def test_buy_stock_is_not_zero_even_with_no_price_swings():
    m = portfolio_metrics([Holding("AAPL", 1, 100, 100)])
    r = risk_score(m, {"AAPL": "Buy"}, annual_volatility=0)
    assert r["volatilityPart"] == 0
    assert r["riskPercent"] == pytest.approx(34.5)  # 0.3*100 + 0.3*15


def test_ten_equal_buy_stocks_are_much_lower_risk_than_one():
    holdings = equal_holdings(10)
    ten = risk_score(portfolio_metrics(holdings), {h.ticker: "Buy" for h in holdings},
                     annual_volatility=25)
    one = risk_score(portfolio_metrics([Holding("AAPL", 1, 100, 100)]), {"AAPL": "Buy"},
                     annual_volatility=25)
    assert ten["concentrationPart"] == 10
    assert ten["ratingsPart"] == 15
    assert ten["riskPercent"] == pytest.approx(27.5)  # 0.4*50 + 0.3*10 + 0.3*15
    assert ten["riskLevel"] == "Low"
    assert 0 < ten["riskPercent"] < one["riskPercent"]


def test_missing_prices_use_neutral_volatility_part():
    m = portfolio_metrics([Holding("AAPL", 1, 100, 100)])
    r = risk_score(m, {"AAPL": "Buy"}, annual_volatility=None)
    assert r["volatilityPart"] == 50
    assert r["volatilityAvailable"] is False
    assert r["annualVolatilityPercent"] is None
    assert r["riskPercent"] == pytest.approx(54.5)  # same as a 25% volatility
    # leaving the argument out means "not available" too
    assert risk_score(m, {"AAPL": "Buy"})["volatilityAvailable"] is False


def test_volatility_part_is_capped_at_100():
    m = portfolio_metrics([Holding("AAPL", 1, 100, 100)])
    r = risk_score(m, {"AAPL": "Sell"}, annual_volatility=80)
    assert r["volatilityPart"] == 100
    assert r["annualVolatilityPercent"] == 80
    assert r["riskPercent"] <= 100


def test_volatility_part_cap_starts_at_fifty_percent_volatility():
    m = portfolio_metrics([Holding("AAPL", 1, 100, 100)])
    assert risk_score(m, {"AAPL": "Buy"}, annual_volatility=50)["volatilityPart"] == 100
    assert risk_score(m, {"AAPL": "Buy"}, annual_volatility=49.9)["volatilityPart"] == pytest.approx(99.8)


def test_risk_never_goes_below_five():
    holdings = equal_holdings(100)
    r = risk_score(portfolio_metrics(holdings), {h.ticker: "Buy" for h in holdings},
                   annual_volatility=0)
    # 0.4*0 + 0.3*1 + 0.3*15 = 4.8, raised to the floor
    assert r["riskPercent"] == 5
    assert r["riskLevel"] == "Low"


def test_unreadable_rating_counts_as_neutral():
    m = portfolio_metrics([Holding("AAPL", 1, 100, 100)])
    assert risk_score(m, {"AAPL": "REVIEW"})["ratingsPart"] == 50


def test_empty_value_is_rejected():
    with pytest.raises(ValueError):
        portfolio_metrics([])
