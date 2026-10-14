import pytest

from piq_server.metrics import Holding, portfolio_metrics, risk_score


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


def test_risk_mixes_concentration_and_ratings():
    m = portfolio_metrics([Holding("MSFT", 10, 250, 300), Holding("AAPL", 5, 200, 200)])
    r = risk_score(m, {"MSFT": "Buy", "AAPL": "Sell"})
    # concentration 62.5; ratings 0.75*0 + 0.25*100 = 25; average = 43.75
    assert r["concentrationPart"] == 62.5
    assert r["ratingsPart"] == 25
    assert r["riskPercent"] == 43.75
    assert r["riskLevel"] == "Medium"


def test_unreadable_rating_counts_as_neutral():
    m = portfolio_metrics([Holding("AAPL", 1, 100, 100)])
    assert risk_score(m, {"AAPL": "REVIEW"})["ratingsPart"] == 50


def test_empty_value_is_rejected():
    with pytest.raises(ValueError):
        portfolio_metrics([])
