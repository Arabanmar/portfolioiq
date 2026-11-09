import time

from fastapi.testclient import TestClient

import app as server
from piq_server import settings

client = TestClient(server.app)


def wait_for(job_id, timeout=10):
    end = time.time() + timeout
    while time.time() < end:
        job = client.get(f"/analysis/{job_id}").json()
        if job["status"] in ("done", "failed"):
            return job
        time.sleep(0.05)
    raise AssertionError("analysis did not finish")


def test_health_reports_fake_mode():
    assert client.get("/health").json()["mode"] == "fake"


def test_full_analysis_then_chat():
    res = client.post("/analysis", json={"holdings": [
        {"ticker": "aapl", "quantity": 10, "purchasePrice": 150, "currentPrice": 200},
        {"ticker": "MSFT", "quantity": 2, "purchasePrice": 300},
    ]})
    assert res.status_code == 202
    job = wait_for(res.json()["jobId"])
    assert job["status"] == "done", job
    result = job["result"]
    assert result["metrics"]["totalValue"] == 2600  # 2000 + 600 (no current price -> purchase price)
    assert {s["ticker"] for s in result["stocks"]} == {"AAPL", "MSFT"}
    assert all(s["rating"] in ("Buy", "Overweight", "Hold", "Underweight", "Sell") for s in result["stocks"])
    risk = result["risk"]
    assert 0 < risk["riskPercent"] <= 100
    # fake mode reports a fixed 25% volatility -> volatility part 50
    assert risk["volatilityAvailable"] is True
    assert risk["annualVolatilityPercent"] == 25
    assert risk["volatilityPart"] == 50
    assert result["summary"]["summary"]
    assert job["progress"] == {"done": 2, "total": 2, "current": None}
    for s in result["stocks"]:
        assert 10 <= s["confidencePercent"] <= 95
        assert s["confidenceLevel"] in ("High", "Medium", "Low")
        assert set(s["votes"]) == {"researchManager", "trader", "final"}
        assert s["votes"]["final"] == s["rating"]
    for s in result["stocks"]:
        assert 0 < len(s["newsSummary"]) <= 700
        assert s["sources"]
        assert 0 < len(s["bullCase"]) <= 500
        assert 0 < len(s["bearCase"]) <= 500
    percents = [s["confidencePercent"] for s in result["stocks"]]
    portfolio = result["confidence"]
    assert min(percents) <= portfolio["confidencePercent"] <= max(percents)
    assert portfolio["confidenceLevel"] in ("High", "Medium", "Low")

    answer = client.post("/chat", json={"jobId": job["jobId"], "question": "Is my portfolio risky?"})
    assert answer.status_code == 200
    assert "Is my portfolio risky?" in answer.json()["answer"]


def test_same_ticker_twice_is_merged():
    res = client.post("/analysis", json={"holdings": [
        {"ticker": "AAPL", "quantity": 10, "purchasePrice": 100, "currentPrice": 200},
        {"ticker": "AAPL", "quantity": 10, "purchasePrice": 200, "currentPrice": 200},
    ]})
    result = wait_for(res.json()["jobId"])["result"]
    assert len(result["stocks"]) == 1
    assert result["metrics"]["totalCost"] == 3000


def test_prices_that_cannot_be_downloaded_still_give_a_risk_score(monkeypatch):
    monkeypatch.setattr("piq_server.jobs.volatility.get_annual_volatility", lambda weights: None)
    res = client.post("/analysis", json={"holdings": [
        {"ticker": "AAPL", "quantity": 1, "purchasePrice": 100}]})
    job = wait_for(res.json()["jobId"])
    assert job["status"] == "done", job
    risk = job["result"]["risk"]
    assert risk["volatilityAvailable"] is False
    assert risk["annualVolatilityPercent"] is None
    assert risk["volatilityPart"] == 50
    assert risk["riskPercent"] > 0


def test_volatility_is_asked_for_with_the_portfolio_weights(monkeypatch):
    seen = {}

    def record(weights):
        seen.update(weights)
        return 25.0

    monkeypatch.setattr("piq_server.jobs.volatility.get_annual_volatility", record)
    res = client.post("/analysis", json={"holdings": [
        {"ticker": "aapl", "quantity": 10, "purchasePrice": 150, "currentPrice": 200},
        {"ticker": "MSFT", "quantity": 2, "purchasePrice": 300},
    ]})
    assert wait_for(res.json()["jobId"])["status"] == "done"
    # 2000 and 600 of 2600, as percentages
    assert set(seen) == {"AAPL", "MSFT"}
    assert abs(seen["AAPL"] - 76.92) < 0.01
    assert abs(seen["MSFT"] - 23.08) < 0.01


def test_portfolio_confidence_is_weighted_by_position_value():
    # fake mode: TSLA gets confidence 90, JNJ gets 40 (see tests/test_agents.py)
    res = client.post("/analysis", json={"holdings": [
        {"ticker": "TSLA", "quantity": 10, "purchasePrice": 150, "currentPrice": 200},
        {"ticker": "JNJ", "quantity": 2, "purchasePrice": 300},
    ]})
    result = wait_for(res.json()["jobId"])["result"]
    by_ticker = {s["ticker"]: s["confidencePercent"] for s in result["stocks"]}
    assert by_ticker == {"TSLA": 90, "JNJ": 40}
    # (2000 x 90 + 600 x 40) / 2600 = 78.46, shown as the whole number 78
    assert result["confidence"] == {"confidencePercent": 78, "confidenceLevel": "High"}


def test_bad_input_is_rejected():
    assert client.post("/analysis", json={"holdings": []}).status_code == 422
    assert client.post("/analysis", json={"holdings": [
        {"ticker": "not a ticker!", "quantity": 1, "purchasePrice": 1}]}).status_code == 422
    assert client.post("/analysis", json={"holdings": [
        {"ticker": "AAPL", "quantity": 0, "purchasePrice": 1}]}).status_code == 422


def test_unknown_job_and_unfinished_chat():
    assert client.get("/analysis/nope").status_code == 404
    assert client.post("/chat", json={"jobId": "nope", "question": "hi"}).status_code == 404


def test_token_is_required_when_configured(monkeypatch):
    monkeypatch.setattr(settings, "SERVER_TOKEN", "secret")
    holdings = {"holdings": [{"ticker": "AAPL", "quantity": 1, "purchasePrice": 1}]}
    assert client.post("/analysis", json=holdings).status_code == 401
    accepted = client.post("/analysis", json=holdings, headers={"X-PIQ-Token": "secret"})
    assert accepted.status_code == 202
    assert client.get("/health").status_code == 200  # health stays open
    # Let the job finish before the next test, so it can't run while a later test has changed the settings.
    monkeypatch.setattr(settings, "SERVER_TOKEN", "")
    wait_for(accepted.json()["jobId"])
