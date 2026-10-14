# Lab 5: Agentic Loop and Delivery

The next piece of work is the doctor's Sprint 3 review of the AI analyst:

1. "How confident is the advice?" There is no confidence score today.
2. "No stock has zero risk." Our risk score treats a Buy rating as 0 risk
   and ignores how much the price actually moves.
3. "Does the bot read news?" It does, but the app never shows it.
4. "Is the analysis neutral?" The agents debate for and against, but the app
   only shows the final decision.

Each issue below is a vertical slice: server logic, the JSON the app reads,
the screen, and tests, in one small change. They are ordered with
"Blocked by" so they are built one at a time on top of each other.

## Issue A: Risk score that is never zero

Blocked by: none

Why: a Buy rating counts as 0 risk, and price swings are ignored.

Server (`ai-server/piq_server/metrics.py`, `jobs.py`):
- New `volatility.py`: one year of daily closes per ticker from yfinance
  (already installed with TradingAgents), daily returns, and the annualised
  portfolio volatility using the covariance of the stocks (so spreading over
  different stocks lowers it). In fake mode return a fixed 25%.
- Ratings part: Buy 15, Overweight 30, Hold 50, Underweight 70, Sell 85,
  REVIEW 50 (no rating is 0).
- Volatility part: annual volatility % x 2, capped at 100 (25% gives 50).
- riskPercent = 0.4 x volatility part + 0.3 x concentration part
  + 0.3 x ratings part, never below 5. If prices can't be downloaded, use 50
  for the volatility part and return `"volatilityAvailable": false`.
- Return `volatilityPart`, `annualVolatilityPercent`, `volatilityAvailable`
  in the `risk` JSON.

App: show the three parts under the risk line, for example
"Price swings 50, Concentration 100, Agent ratings 70".

Tests: pytest for the new formula (one stock, ten stocks, no zero for a Buy,
fallback when prices are missing). JUnit for parsing the new fields.

Done when: `/run-unit-tests` passes and the risk for a single Buy stock is
above 0.

## Issue B: Confidence score for the advice

Blocked by: Issue A

Why: the doctor asked how confident the Buy/Sell advice is.

Server (`agents.py`, new `confidence.py`):
- Read three votes from the TradingAgents state: the Research Manager's
  recommendation (`investment_plan`, line `**Recommendation**: X`), the
  Trader's action (`trader_investment_plan`, line `**Action**: X`) and the
  final rating.
- Direction: Buy/Overweight = up, Hold = neutral, Underweight/Sell = down.
- Agreement with the final rating: 3 of 3 = 90, 2 of 3 = 65, 1 of 3 = 40.
  Minus 10 for each analyst report that came back empty. Keep it between 10
  and 95. Level: High from 75, Medium from 50, otherwise Low.
- Per stock: `confidencePercent`, `confidenceLevel`, `votes`.
  Portfolio: value-weighted average in `result.confidence`.
- It measures how much the agents agree, not the chance of making money.
  Say that in the app and in the summary prompt.

App: "Confidence: 90% (High). How much the AI agents agree. Not a
guarantee." per stock and for the portfolio.

Tests: pytest for 3/3, 2/3, 1/3, missing reports, the clamp; JUnit for
parsing.

## Issue C: Show what the agents read (news)

Blocked by: Issue B

Server: per stock, `newsSummary` (the first 700 characters of the News
Analyst's report, plain text) and `sources` (the data sources used, for
example "Yahoo Finance news, past 7 days", "Yahoo Finance prices and
indicators", "SEC EDGAR financial statements").

App: a "What the agents read" section per stock.

Tests: pytest that the fields exist and are trimmed; fake mode fills them.

## Issue D: The case for and against (neutrality)

Blocked by: Issue C

Server: per stock, `bullCase` and `bearCase`: the first 500 characters of
the Bull and Bear researchers' arguments (`investment_debate_state`).

App: "For" and "Against" under each stock, before the final rating.

Tests: pytest that both are present and trimmed.

## The loop for each issue (Claude Code)

1. Plan: read CLAUDE.md and the issue, list the files and tests, wait for
   approval (human checkpoint 1).
2. Implement on a branch `lab5/<issue-letter>-<short-name>`.
3. Test: `/run-unit-tests` must say PASS. Fix and repeat if not.
4. Review: the `code-reviewer` agent; fix should-fix items (checkpoint 2:
   the human reads the review).
5. Commit, push, and open a pull request with "Closes #N".
