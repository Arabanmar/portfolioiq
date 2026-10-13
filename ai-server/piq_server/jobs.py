"""Analysis jobs. One TradingAgents run takes minutes per stock, so the app
starts a job, then asks for its progress every few seconds.

Jobs run one at a time on a single background thread (to stay inside the
Gemini free-tier rate limits) and are kept in memory only: restarting the
server forgets them.
"""

from __future__ import annotations

import threading
import traceback
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import date

from . import agents, summarizer
from .metrics import Holding, portfolio_metrics, risk_score

_executor = ThreadPoolExecutor(max_workers=1)
_jobs: dict[str, dict] = {}
_lock = threading.Lock()


def start(holdings: list[Holding]) -> str:
    job_id = uuid.uuid4().hex
    with _lock:
        _jobs[job_id] = {
            "jobId": job_id,
            "status": "queued",
            "progress": {"done": 0, "total": len(holdings), "current": None},
            "result": None,
            "error": None,
        }
    _executor.submit(_run, job_id, holdings)
    return job_id


def get(job_id: str) -> dict | None:
    with _lock:
        job = _jobs.get(job_id)
        return None if job is None else {**job, "progress": dict(job["progress"])}


def _update(job_id: str, **changes) -> None:
    with _lock:
        job = _jobs[job_id]
        progress = changes.pop("progress", None)
        if progress:
            job["progress"].update(progress)
        job.update(changes)


def _run(job_id: str, holdings: list[Holding]) -> None:
    try:
        _update(job_id, status="running")
        metrics = portfolio_metrics(holdings)
        positions = [{"ticker": h.ticker, "quantity": h.quantity, "average_price": h.purchase_price}
                     for h in holdings]
        trade_date = date.today().isoformat()

        stocks = []
        for i, h in enumerate(holdings):
            _update(job_id, progress={"current": h.ticker})
            stocks.append(agents.analyze_stock(h.ticker, trade_date, positions))
            _update(job_id, progress={"done": i + 1})
        _update(job_id, progress={"current": None})

        ratings = {s["ticker"]: s["rating"] for s in stocks}
        risk = risk_score(metrics, ratings)
        summary = summarizer.summarize(metrics, risk, stocks)

        result = {
            "analysisDate": trade_date,
            "metrics": metrics,
            "risk": risk,
            "summary": summary,
            "stocks": [{"ticker": s["ticker"], "rating": s["rating"], "decision": s["decision"]}
                       for s in stocks],
        }
        _update(job_id, status="done", result=result)
    except Exception as exc:  # report any failure to the app instead of hanging
        traceback.print_exc()
        _update(job_id, status="failed", error=_friendly_error(exc))


def _friendly_error(exc: Exception) -> str:
    text = str(exc)
    lowered = text.lower()
    if "api key" in lowered or "api_key" in lowered or "permission" in lowered or "401" in text or "403" in text:
        return "The AI service rejected the request. Check the Gemini API key in ai-server/.env."
    if "429" in text or "quota" in lowered or "rate" in lowered:
        return "The AI service's free limit was reached. Wait a minute and try again."
    return "The analysis failed. See the server window for details."
