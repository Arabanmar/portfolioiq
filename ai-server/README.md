# PortfolioIQ AI server

The AI part of PortfolioIQ (Stories 005 and 006). It runs
[TradingAgents](https://github.com/TauricResearch/TradingAgents), the
multi-agent analyst framework the doctor chose, with Google Gemini, and
gives the Android app a small HTTP API.

## How it works

1. The app's **AI Analysis** button sends the user's holdings (ticker,
   quantity, purchase price, live price) to `POST /analysis`.
2. The server calculates the portfolio numbers in plain Python
   (`piq_server/metrics.py`): allocation, largest position, diversification
   score, and a risk percentage. The price-swing part of the risk comes from
   a year of daily prices (`piq_server/volatility.py`, downloaded with
   yfinance). No AI is involved in any number.
3. For each stock, TradingAgents runs its team of agents (market, news and
   fundamentals analysts, bull/bear researchers, trader, risk team,
   portfolio manager) and returns a rating: Buy, Overweight, Hold,
   Underweight or Sell. This takes a few minutes per stock.
4. Gemini writes the summary, strengths and weaknesses from those numbers
   and ratings, and answers the chat questions (`POST /chat`) from the same data.

The app checks `GET /analysis/{jobId}` every few seconds to show progress.

**Risk percentage** = 0.4 x price swings + 0.3 x concentration + 0.3 x the
agents' ratings, never below 5. Under 35 is Low, under 65 Medium, else High.
- *Price swings* = the portfolio's annual volatility % x 2, at most 100 (25%
  gives 50). It uses the covariance of the stocks' daily returns over the last
  year, so spreading over different stocks lowers it. If the prices can't be
  downloaded, 50 is used and the app is told (`volatilityAvailable: false`).
- *Concentration* = sum of squared weights x 100 (everything in one stock = 100).
- *Agents' ratings* = Buy 15, Overweight 30, Hold 50, Underweight 70, Sell 85
  (unreadable rating 50), weighted by allocation. No rating is 0, because no
  stock has zero risk.

**Confidence** = how much the three agents that judge each stock agree
(`piq_server/confidence.py`): the Research Manager's recommendation, the
Trader's action and the final rating, compared by direction (Buy and
Overweight = up, Hold = neutral, Underweight and Sell = down). All three the
same way = 90, two of three = 65, one of three = 40, minus 10 for each empty
analyst report, kept between 10 and 95. High from 75, Medium from 50, else Low.
Each stock gets `confidencePercent`, `confidenceLevel` and `votes`; the
portfolio gets the average weighted by position value, rounded to a whole
number, in `result.confidence`. It measures how much the agents agree, not the
chance of making money.

**What the agents read.** Each stock also carries `newsSummary` (the start of
the News Analyst's report as plain text, at most 700 characters, cut at a whole
word) and `sources` (the data sources each analyst that produced a report can
use, from TradingAgents' configured data vendors, for example "Yahoo Finance
prices and indicators", "Yahoo Finance news, past 7 days", "SEC EDGAR financial
statements"). `sources` is not a log of the exact calls the model made:
TradingAgents keeps each analyst's tool calls out of its final state. Only the
first vendor of a chain is named (a non-US company, or an EDGAR failure, falls
back to Yahoo Finance), "past 7 days" is what the News Analyst is told rather
than the exact dates it picked, a source is listed even if its tools returned
no data, and the News Analyst's optional macro-data (FRED) and Polymarket tools
are not listed. The app labels the list "Data sources the agents can use". See
`piq_server/reading.py` and `piq_server/text.py`.

**The case for and against.** Each stock also carries `bullCase` and `bearCase`:
the first argument of TradingAgents' Bull and Bear researchers
(`investment_debate_state`), with the "Bull Analyst:" / "Bear Analyst:" label
removed, as plain text, at most 500 characters, cut at a whole word. They are
LLM-written debate speeches, each told to advocate one side, and the bear's is
written after reading the bull's and rebuts it, so it reads conversationally.
The app shows them as "For" and "Against" before the final rating. See
`piq_server/debate.py`.

**Saved results.** One stock takes minutes of Gemini calls, so a finished
analysis is saved to `ai-server/data/cache/<date>/<TICKER>.json` (fake mode uses
`<TICKER>.fake.json`, so a demo run never replaces a real result) and reused
instead of running the agents again (`piq_server/cache.py`). A saved result is
only reused when it was made today, for the **same holdings** (TradingAgents' Trader,
Portfolio Manager and risk debators are told what you hold, so a changed book
is a different run), with the same settings (fake or real mode, analysts,
Gemini models), by the same code (a digest of the files that turn TradingAgents'
output into the saved fields, so editing one never serves old fields), and with
every field of the right type. Only a complete result is saved: a failed run
saves nothing, and neither does one with no readable rating, no decision, or an
empty report from an analyst that is switched on. Portfolio numbers, risk, the
portfolio-level confidence and the summary are always computed fresh from your
current holdings; a stock's own confidence, news and for/against come from the
saved result. Each stock carries `fromCache`, the job's progress says when the
stock being "analysed" is a saved result, and the server window logs how many
seconds each stock took. A saved result can be hours old (the file records when
it was saved); the app labels it "Saved result from today". To force a fresh
analysis, delete that stock's file (or the day's folder). Old days' folders are
never read again and are not cleaned up automatically.

**Diversification score** = 100 x (1 - sum of squared weights): 0 means
everything in one stock.

## Run it (Windows)

Needs Python 3.13 (3.11, 3.12 or 3.14 also work) and Git. Python 3.15 does not
work yet: some packages have no ready-made Windows build for it. If you have
the Python install manager, `py install 3.13` adds 3.13 next to any other version;
`setup_server.bat` picks it automatically.

1. Double-click `setup_server.bat` (one time; takes a few minutes).
2. Open `.env` in Notepad and paste your Gemini key after `GOOGLE_API_KEY=`.
3. Double-click `start_server.bat` and leave the window open.
4. Run the app in the Android emulator and tap **AI Analysis** on the dashboard.

To try the screens without using Gemini, set `PIQ_FAKE_AI=1` in `.env`.
`run_tests.bat` runs the automated tests (fake mode, no key needed).

The Gemini key stays in `.env` on the laptop, which is gitignored. It is
never in the app or on GitHub. The output is AI-generated information for a
student project, not financial advice.
