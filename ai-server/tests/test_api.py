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
    assert 0 <= result["risk"]["riskPercent"] <= 100
    assert result["summary"]["summary"]
    assert job["progress"] == {"done": 2, "total": 2, "current": None}

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
    assert client.post("/analysis", json=holdings, headers={"X-PIQ-Token": "secret"}).status_code == 202
    assert client.get("/health").status_code == 200  # health stays open
