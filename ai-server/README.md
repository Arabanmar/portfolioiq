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
