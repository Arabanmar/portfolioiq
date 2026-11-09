# PortfolioIQ

An Android app for tracking a personal stock portfolio, built for CCSW 431
(Mobile Programming) at the University of Jeddah. Users create an account,
sign in, add the stocks they hold, and see a live dashboard of their
portfolio's value and performance. Later stories add an LLM-powered
portfolio analysis and chat assistant.

See [`CLAUDE.md`](CLAUDE.md) for the architecture summary, coding
standards, and definition of done that every change in this repo follows.

## Tech stack

- **Language:** Java (not Kotlin)
- **Platform:** Android, built in Android Studio
- **Auth + data:** Firebase Authentication + Cloud Firestore
- **Live stock prices:** [Finnhub](https://finnhub.io) free tier
- **Architecture:** clean architecture (domain / data / presentation layers)

## Setup

Two files are required to build this project and are **not committed to
git** (both are in `.gitignore` on purpose — one holds a Firebase client
config, the other a personal API key):

1. **`app/google-services.json`** — your Firebase project's Android config
   file. Get it from the Firebase Console for the `portfolioiq-ccsw431`
   project (Project settings → your Android app → download
   `google-services.json`), or ask a teammate who already has Firebase
   access to share it. Place it at `app/google-services.json`.
2. **`local.properties`** — Android Studio usually generates this
   automatically with your local SDK path (`sdk.dir=...`). Add one more
   line to it for live stock prices:
   ```
   FINNHUB_API_KEY=your_own_free_finnhub_api_key
   ```
   Get a free key at [finnhub.io](https://finnhub.io) (sign up, copy the
   key from your dashboard). Without this line, the app still builds and
   runs, but the portfolio dashboard won't be able to fetch live prices.

   Optional, for the AI analysis: `AI_SERVER_URL=http://10.0.2.2:8000`
   is the default (the laptop as seen from the emulator), so you only need
   this line to point somewhere else. If the server sets
   `PIQ_SERVER_TOKEN`, add the same value as `AI_SERVER_TOKEN=...`.

3. **The AI server** (only for the AI Analysis and chat screens): see
   [`ai-server/README.md`](ai-server/README.md). In short: run
   `ai-server/setup_server.bat` once, put your Gemini key in
   `ai-server/.env`, then keep `ai-server/start_server.bat` running while
   you use the app.

Once both are in place: open the project in Android Studio, let Gradle
sync, and run it on an emulator or device (**Run → Run 'app'**), or just
compile it without running anything (**Build → Compile All Sources**, or
**Build → Make Project**) to confirm it builds.

### Quick start (in this order)

1. Start the AI server: double-click `ai-server/start_server.bat` and keep
   the window open. `http://127.0.0.1:8000/health` should say `"status":"ok"`.
2. Start the emulator from Android Studio's Device Manager and wait for the
   home screen.
3. Press **Run** in Android Studio.
4. Create an account, add a stock from the dropdown, then tap **AI Analysis**.
   One stock takes a few minutes the first time; the same portfolio again on
   the same day reuses the saved result and finishes in seconds.

### If the app says it can't reach the AI server

On some laptops the emulator cannot reach the default address
`http://10.0.2.2:8000` (the request just times out, while the browser on the
laptop works). Forward the port over adb instead:

1. In Android Studio's Terminal, with the emulator running:
   ```
   adb reverse tcp:8000 tcp:8000
   ```
   (adb is in `%LOCALAPPDATA%\Android\Sdk\platform-tools`). Run it again
   after the emulator restarts.
2. Add this line to `local.properties`, then press **Run** again:
   ```
   AI_SERVER_URL=http://127.0.0.1:8000
   ```

The same two steps work for a real phone connected with USB debugging.
Every request from the app shows up as a line in the server window, so you
can see whether the app is reaching it.

### Running tests

One command runs everything and prints a summary with the exact numbers
(Android unit tests through Gradle, and the AI server tests with pytest in
fake AI mode, so no key is used):

```
bash .claude/skills/run-unit-tests/run_tests.sh
```

In Claude Code the same thing is `/run-unit-tests`. You can also right-click
the `test` folder in Android Studio and choose **Run Tests**.

### Keys

No key is committed. `app/google-services.json`, `local.properties` and
`ai-server/.env` are all in `.gitignore`.

## Branch strategy

- `main` is protected: every change goes through a pull request, no direct
  pushes (enforced by a GitHub ruleset, not just a convention).
- Feature branches are named `feature/<short-description>`
  (e.g. `feature/sprint2-dashboard-holdings`). A branch maps to one
  sprint's work, one story, or one focused fix — not a grab-bag of
  unrelated changes.
- Documented exception: Part Two's "Lab" deliverables (CCSW 431
  Agentic Development Workflow) use `lab<n>/<short-description>`
  instead (e.g. `lab2/context-engineering`, `lab3/mcp-and-memory`),
  to keep lab work visibly separate from Sprint feature work in the
  branch list. Same PR-and-review rule applies either way.
- Open a PR into `main` when the branch is ready; a human on the team
  reviews and merges it. The agent opens/pushes when it can, but a human
  always does the actual merge.

## Commit message convention

- First line: short, imperative summary (`Add Sprint 1 JVM unit test
  suite`, not `Added tests` or `tests`).
- For anything non-trivial, a body explaining *why*, not just *what* —
  especially for a decision a reviewer wouldn't otherwise know the reason
  for (e.g. "chose Finnhub over Alpha Vantage because...").
- Reference the story/lab a commit belongs to when relevant (e.g.
  "Story 004", "Lab 2").

## Project structure

```
app/src/main/java/com/portfolioiq/
├── domain/          # Business logic. No Android or Firebase imports.
│   ├── model/        # Plain data classes (User, StockHolding, ...)
│   ├── repository/   # Interfaces the data layer implements
│   └── usecase/       # One class per user action, execute() pattern
├── data/            # Implementations of the domain repository interfaces
│   └── repository/   # Firebase/Firestore/Finnhub-backed implementations
└── presentation/    # Activities and layouts (UI)
    ├── auth/
    ├── dashboard/
    └── portfolio/
```

## Course context

This repo also serves as the submission artifact for the CCSW 431
"Agentic Development Workflow" assignment (Labs 1-5) — see `references/`
for the project brief and branch-protection evidence, and `CLAUDE.md` for
the Lab 2 context-engineering deliverable.
