"""Turns the computed numbers and the per-stock TradingAgents decisions into a
portfolio summary, strengths and weaknesses, and answers chat questions.

The model is given the numbers; it is told not to calculate new ones.
"""

from __future__ import annotations

import json
import re

from . import settings

DECISION_CHARS = 1500  # how much of each stock's final decision the model sees

SUMMARY_PROMPT = """You are the portfolio analyst inside the PortfolioIQ app.
Below is a user's stock portfolio. All numbers were already calculated by the
app. Use only these numbers: do not calculate, estimate or invent any other
number. Each stock was analysed by a team of AI agents (TradingAgents); their
rating and final decision are included.

PORTFOLIO NUMBERS (JSON):
{metrics}

RISK (JSON, riskPercent is 0-100):
{risk}

PER-STOCK AGENT DECISIONS:
{decisions}

Reply with JSON only, no markdown, in exactly this shape:
{{"summary": "3-4 plain-English sentences about the whole portfolio",
  "strengths": ["2 to 4 short points"],
  "weaknesses": ["2 to 4 short points"],
  "diversificationComment": "1-2 sentences about how diversified it is"}}
This is general information, not financial advice."""

CHAT_PROMPT = """You are the portfolio assistant inside the PortfolioIQ app.
Answer the user's question about their own portfolio using only the data
below. Do not calculate or invent numbers that are not in the data. If the
question is not about this portfolio or investing, say you can only help with
their portfolio. Keep the answer under 150 words, in plain English. This is
general information, not financial advice.

PORTFOLIO NUMBERS (JSON):
{metrics}

RISK (JSON):
{risk}

PER-STOCK AGENT DECISIONS:
{decisions}

PORTFOLIO SUMMARY:
{summary}

USER QUESTION: {question}"""


def _decisions_text(stocks: list[dict]) -> str:
    parts = []
    for s in stocks:
        decision = (s.get("decision") or "").strip()[:DECISION_CHARS]
        parts.append(f"--- {s['ticker']} (rating: {s['rating']}) ---\n{decision}")
    return "\n\n".join(parts)


def _ask_gemini(prompt: str) -> str:
    from langchain_google_genai import ChatGoogleGenerativeAI

    llm = ChatGoogleGenerativeAI(model=settings.DEEP_MODEL, google_api_key=settings.GOOGLE_API_KEY)
    content = llm.invoke(prompt).content
    if isinstance(content, list):  # some versions return a list of content parts
        content = "".join(p.get("text", "") if isinstance(p, dict) else str(p) for p in content)
    return str(content).strip()


def _parse_json(text: str) -> dict:
    match = re.search(r"\{.*\}", text, re.DOTALL)
    if not match:
        raise ValueError("no JSON object in model reply")
    data = json.loads(match.group(0))
    return {
        "summary": str(data.get("summary", "")).strip(),
        "strengths": [str(x).strip() for x in data.get("strengths", []) if str(x).strip()],
        "weaknesses": [str(x).strip() for x in data.get("weaknesses", []) if str(x).strip()],
        "diversificationComment": str(data.get("diversificationComment", "")).strip(),
    }


def summarize(metrics: dict, risk: dict, stocks: list[dict]) -> dict:
    if settings.FAKE_AI:
        return _fake_summary(metrics, risk)
    prompt = SUMMARY_PROMPT.format(
        metrics=json.dumps(metrics, indent=1),
        risk=json.dumps(risk, indent=1),
        decisions=_decisions_text(stocks),
    )
    reply = _ask_gemini(prompt)
    try:
        return _parse_json(reply)
    except (ValueError, json.JSONDecodeError):
        # Keep the text rather than failing the whole analysis.
        return {"summary": reply, "strengths": [], "weaknesses": [], "diversificationComment": ""}


def answer(question: str, result: dict) -> str:
    if settings.FAKE_AI:
        m = result["metrics"]
        return (f"(Fake mode) Your portfolio is worth ${m['totalValue']:,.2f} across "
                f"{m['numberOfHoldings']} stocks. You asked: \"{question}\"")
    prompt = CHAT_PROMPT.format(
        metrics=json.dumps(result["metrics"], indent=1),
        risk=json.dumps(result["risk"], indent=1),
        decisions=_decisions_text(result["stocks"]),
        summary=result["summary"].get("summary", ""),
        question=question,
    )
    return _ask_gemini(prompt)


def _fake_summary(metrics: dict, risk: dict) -> dict:
    return {
        "summary": (f"(Fake mode) {metrics['numberOfHoldings']} holdings worth "
                    f"${metrics['totalValue']:,.2f}, risk {risk['riskPercent']}% ({risk['riskLevel']})."),
        "strengths": [f"Total profit/loss is {metrics['totalProfitLossPercent']}%."],
        "weaknesses": [f"{metrics['largestPosition']} is {metrics['largestPositionPercent']}% of the portfolio."],
        "diversificationComment": f"Diversification score {metrics['diversificationScore']} out of 100.",
    }
