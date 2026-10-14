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
   score, and a risk percentage. No AI is involved in any number.
3. For each stock, TradingAgents runs its team of agents (market, news and
   fundamentals analysts, bull/bear researchers, trader, risk team,
   portfolio manager) and returns a rating: Buy, Overweight, Hold,
   Underweight or Sell. This takes a few minutes per stock.
4. Gemini writes the summary, strengths and weaknesses from those numbers
   and ratings, and answers the chat questions (`POST /chat`) from the same data.

The app checks `GET /analysis/{jobId}` every few seconds to show progress.

**Risk percentage** = half concentration (sum of squared weights x 100),
half the agents' ratings (Buy 0, Overweight 25, Hold 50, Underweight 75,
Sell 100, weighted by allocation). Under 35 is Low, under 65 Medium, else High.
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
