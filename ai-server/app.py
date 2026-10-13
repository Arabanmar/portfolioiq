"""PortfolioIQ AI server: the bridge between the Android app and TradingAgents.

Run it with start_server.bat (Windows), or:
    uvicorn app:app --host 0.0.0.0 --port 8000

Endpoints
    GET  /health                 is the server up, and in real or fake mode
    POST /analysis               start analysing a portfolio -> {"jobId"}
    GET  /analysis/{jobId}       progress, then the result when done
    POST /chat                   ask a question about a finished analysis
"""

from __future__ import annotations

import re

from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, Field, field_validator

from piq_server import jobs, settings, summarizer
from piq_server.metrics import Holding

app = FastAPI(title="PortfolioIQ AI server")

TICKER_RE = re.compile(r"^[A-Z][A-Z0-9.\-]{0,9}$")


def check_token(x_piq_token: str | None = Header(default=None)) -> None:
    if settings.SERVER_TOKEN and x_piq_token != settings.SERVER_TOKEN:
        raise HTTPException(status_code=401, detail="Missing or wrong X-PIQ-Token.")


class HoldingIn(BaseModel):
    ticker: str
    quantity: float = Field(gt=0)
    purchasePrice: float = Field(gt=0)
    currentPrice: float | None = Field(default=None, gt=0)

    @field_validator("ticker")
    @classmethod
    def ticker_format(cls, v: str) -> str:
        v = v.strip().upper()
        if not TICKER_RE.match(v):
            raise ValueError("not a valid ticker")
        return v


class AnalysisRequest(BaseModel):
    holdings: list[HoldingIn] = Field(min_length=1)


class ChatRequest(BaseModel):
    jobId: str
    question: str = Field(min_length=1, max_length=500)


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "mode": "fake" if settings.FAKE_AI else "real",
            "aiKeyConfigured": bool(settings.GOOGLE_API_KEY)}


@app.post("/analysis", status_code=202, dependencies=[Depends(check_token)])
def start_analysis(req: AnalysisRequest) -> dict:
    if len(req.holdings) > settings.MAX_HOLDINGS:
        raise HTTPException(status_code=400,
                            detail=f"At most {settings.MAX_HOLDINGS} stocks can be analysed at once.")
    if not settings.FAKE_AI and not settings.GOOGLE_API_KEY:
        raise HTTPException(status_code=503, detail="The server has no Gemini API key (GOOGLE_API_KEY in .env).")

    # The same stock entered twice is analysed once, with the lots combined.
    merged: dict[str, Holding] = {}
    for h in req.holdings:
        price = h.currentPrice or h.purchasePrice
        if h.ticker in merged:
            old = merged[h.ticker]
            qty = old.quantity + h.quantity
            avg = (old.cost + h.quantity * h.purchasePrice) / qty
            merged[h.ticker] = Holding(h.ticker, qty, avg, price)
        else:
            merged[h.ticker] = Holding(h.ticker, h.quantity, h.purchasePrice, price)

    return {"jobId": jobs.start(list(merged.values()))}


@app.get("/analysis/{job_id}", dependencies=[Depends(check_token)])
def get_analysis(job_id: str) -> dict:
    job = jobs.get(job_id)
    if job is None:
        raise HTTPException(status_code=404, detail="Unknown analysis. It may have been lost when the server restarted.")
    return job


@app.post("/chat", dependencies=[Depends(check_token)])
def chat(req: ChatRequest) -> dict:
    job = jobs.get(req.jobId)
    if job is None:
        raise HTTPException(status_code=404, detail="Unknown analysis. Run the analysis again.")
    if job["status"] != "done":
        raise HTTPException(status_code=409, detail="The analysis is not finished yet.")
    try:
        return {"answer": summarizer.answer(req.question.strip(), job["result"])}
    except Exception:
        raise HTTPException(status_code=502, detail="The AI could not answer right now. Try again.")
