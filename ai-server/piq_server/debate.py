"""The case for and against each stock, from the TradingAgents debate (Issue D), in plain Python (no AI).

TradingAgents' Bull and Bear researchers each argue one side. It keeps what each
said in state["investment_debate_state"]["bull_history"] and ["bear_history"]:
every argument starts with a "Bull Analyst:" or "Bear Analyst:" label, and the
arguments of later rounds are appended after it. Only the first argument of each
side is shown here, with its label removed, as plain text and at most
DEBATE_CASE_CHARS characters.

These are LLM-written debate speeches, each told to advocate one side. The bear's
is written after reading the bull's and is told to rebut it, so it reads
conversationally. What the agents said is shown as it is, not rewritten.
"""

from __future__ import annotations

import re

from .text import as_text, plain_text, shorten

DEBATE_CASE_CHARS = 500


def first_argument(history, speaker: str) -> str:
    """The text of the first argument in a debate history, without its "<speaker> Analyst:" label.

    speaker: "Bull" or "Bear". A label only counts at the start of a line, so one
    inside a sentence does not split the text. A history with no label at all is
    taken whole; a label with nothing after it gives "". A line of the reply that
    itself starts with the label is read as the start of a later argument.

    The researchers' prompts show the debate history, whose lines all start with
    the label, so a model may repeat it at the start of its reply ("Bear Analyst:
    Bear Analyst: ..."); any such repeats are removed too.
    """
    name = re.escape(speaker)
    label = re.compile(rf"^[ \t]*{name}[ \t]+Analyst[ \t]*:", re.IGNORECASE | re.MULTILINE)
    repeated = re.compile(rf"^(?:\s*\**[ \t]*{name}[ \t]+Analyst[ \t]*:\**)+", re.IGNORECASE)
    parts = label.split(as_text(history))
    argument = parts[0] if len(parts) == 1 else next(
        (part for part in parts[1:] if part.strip()), "")
    return repeated.sub("", argument)


def case_text(history, speaker: str) -> str:
    """One side's first argument as plain text, at most DEBATE_CASE_CHARS characters."""
    return shorten(plain_text(first_argument(history, speaker)), DEBATE_CASE_CHARS)


def stock_debate(state) -> dict:
    """{"bullCase", "bearCase"} for one stock, from a TradingAgents final state.

    Either is "" when the debate state is missing or that side said nothing.
    """
    debate = state.get("investment_debate_state") if isinstance(state, dict) else None
    if not isinstance(debate, dict):
        debate = {}
    return {
        "bullCase": case_text(debate.get("bull_history"), "Bull"),
        "bearCase": case_text(debate.get("bear_history"), "Bear"),
    }
