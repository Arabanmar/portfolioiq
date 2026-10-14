# Lab 5: Agentic Loop and Delivery

The next piece of work is the doctor's Sprint 3 review of the AI analyst:

1. "How confident is the advice?" There is no confidence score today.
2. "No stock has zero risk." Our risk score treats a Buy rating as 0 risk
   and ignores how much the price actually moves.
3. "Does the bot read news?" It does, but the app never shows it.
4. "Is the analysis neutral?" The agents debate for and against, but the app
   only shows the final decision.
5. "Why does it take about 4 minutes per stock?" Each stock needs roughly
   15 to 25 Gemini calls in a row, plus live data downloads, and the free
   Gemini plan limits requests per minute.

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

## Issue E: Reuse today's result (faster analysis)

Blocked by: Issue D

Why: the doctor asked why one stock takes about 4 minutes. Most of the time
is Gemini calls, so the cheapest speed-up with no quality loss is to not
repeat work: a stock analysed today does not need the agents again today.

Server (`agents.py`, new `cache.py`):
- Save each stock's TradingAgents result (rating, decision, confidence,
  news, for and against) to `ai-server/data/cache/<date>/<TICKER>.json`
  after a successful run.
- Before running the agents for a stock, use the saved file if it exists
  for today's date. Portfolio numbers, risk and the summary are still
  computed fresh, because the quantities may have changed.
- Add `"fromCache": true/false` per stock, and log the seconds each stock
  took in the server window.
- Fake mode: the cache works the same way (tests use a temporary folder).

App: next to a stock, show "Saved result from today" when `fromCache` is
true. The progress text says "Using today's saved result for AAPL".

Tests: pytest that the second analysis of the same stock on the same day
does not call the agents again, that a new day does, and that a failed run
is not saved.

Done when: running AAPL twice in a row, the second run finishes in seconds.

## The loop for each issue (Claude Code)

1. Plan: read CLAUDE.md and the issue, list the files and tests, wait for
   approval (human checkpoint 1).
2. Implement on a branch `lab5/<issue-letter>-<short-name>`.
3. Test: `/run-unit-tests` must say PASS. Fix and repeat if not.
4. Review: the `code-reviewer` agent; fix should-fix items (checkpoint 2:
   the human reads the review).
5. Commit, push, and open a pull request with "Closes #N".
