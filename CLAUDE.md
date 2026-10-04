# CLAUDE.md — PortfolioIQ context file

This file is the operating contract for any AI coding agent working on
this repo (originally written for Claude Code, but the conventions below
apply regardless of which agent reads them). It's also Lab 2's main
deliverable for CCSW 431's Agentic Development Workflow assignment.
Read this before making any change — it exists so the agent doesn't have
to rediscover these conventions from scratch, or guess and get them
wrong, every session.

## Project overview

PortfolioIQ is an Android app (Java, not Kotlin) for tracking a personal
stock portfolio: sign up, sign in, add the stocks you hold, see a
dashboard of your portfolio's live value and performance. Built for
CCSW 431 (Mobile Programming), University of Jeddah, across Scrum sprints
that map to the course's own Week 3/5/7/9/10 milestones.

Stories shipped so far: 001 Registration, 002 Login & Authentication
(Sprint 1); 003 Add/Edit/Delete Stock Holding, 004 Portfolio Dashboard &
Calculations (Sprint 2). Stories 005 (AI Portfolio Analysis) and 006 (AI
Chat Assistant) are planned but not started — they will use Firebase AI
Logic (Gemini) and must follow the architecture rule below.

External services: Firebase Authentication + Cloud Firestore (accounts
and data storage), Finnhub (free tier, live stock quotes). No custom
backend server — the Android app talks to these directly.

## Architecture summary

Clean architecture, three layers, one-way dependency (presentation →
domain ← data; domain never imports from either):

```
domain/          Business logic only. Zero Android SDK or Firebase
                  imports anywhere in this package. This is what makes
                  domain code testable as plain JVM unit tests, with no
                  emulator — verify a change here doesn't accidentally
                  pull in an android.* or com.google.firebase.* import.
  model/          Plain data holders (User, StockHolding, HoldingPosition,
                  PortfolioSummary). No behavior beyond simple derived
                  getters / withX() copy helpers.
  repository/     Interfaces only (AuthRepository, PortfolioRepository,
                  StockPriceRepository). The data/ layer implements them;
                  domain and presentation code depend on the interface,
                  never the implementation class, directly.
  usecase/        One class per user action (RegisterUseCase,
                  AddStockHoldingUseCase, GetPortfolioUseCase, ...). See
                  "UseCase convention" below.

data/repository/  One implementation class per domain repository
                  interface, named <TechnologyName><InterfaceName>Impl
                  (FirebaseAuthRepositoryImpl, FirestorePortfolioRepositoryImpl,
                  FinnhubStockPriceRepositoryImpl). This is the only
                  package allowed to import FirebaseAuth, FirebaseFirestore,
                  or make raw HTTP calls.

presentation/     Activities + XML layouts, one subpackage per feature
                  (auth/, dashboard/, portfolio/). An Activity constructs
                  its own repository implementation and use case(s) in
                  onCreate(), wires them to views, and does nothing else
                  business-logic-shaped. No Activity should contain a
                  validation rule or a calculation — those belong in a
                  use case.
```

Data flow for a typical write (e.g. adding a holding): Activity reads raw
`String`s off `EditText`s → calls a UseCase's `execute()` → UseCase
validates/parses and, only if valid, calls the repository interface →
the data-layer implementation talks to Firebase/Finnhub and calls back
with success or a user-safe error message → Activity updates the UI.

**Architecture rule that must hold for every future story**: the app
computes every number shown to the user (position value, P/L, P/L%,
allocation%, etc. — see `GetPortfolioUseCase`). When Story 005/006 add
the LLM (Firebase AI Logic / Gemini), the LLM is only ever handed
already-computed numbers to interpret or explain — it never calculates
anything itself, and its responses should be structured JSON the app
parses into UI, not free text.

## UseCase convention

Every use case follows the same shape — copy an existing one
(`AddStockHoldingUseCase` is a good template) rather than inventing a new
pattern:

- One public `execute(...)` method (Java has no Kotlin-style
  `operator fun invoke`, so this is the closest equivalent). Constructor
  takes the repository interface(s) it needs.
- Parameters are the **raw strings** straight off the UI (e.g.
  `execute(String ticker, String quantityText, String purchasePriceText, ...)`),
  not pre-parsed values — parsing and validation are the use case's job,
  not the Activity's.
- Validate first, in order, calling `callback.onError(message)` and
  `return`ing immediately on the first failure. Only call the repository
  once every check has passed.
- Error messages passed to `onError` must already be safe to show the
  user directly (no raw exception text, no stack traces) — the
  repository implementation is responsible for translating a raw
  Firebase/network exception into one of these before it ever reaches a
  use case or callback.
- Results come back via a callback interface, not a return value (every
  Firebase/network call here is async). Use `ResultCallback<T>` (in
  `domain.repository`) for anything new — see "known inconsistency"
  below for why `AuthRepository` still uses the older `AuthCallback<T>`.

## Coding standards

- **No `android.util.Patterns`, or any other `android.*`/Google Play
  Services class, anywhere under `domain/`.** It breaks plain JVM unit
  tests (the Android stub jar throws/returns null for these outside
  instrumentation) — this already bit Sprint 1 once and was fixed by
  extracting `EmailValidator` to use plain `java.util.regex.Pattern`
  instead. If a change needs an Android API, it belongs in `data/` or
  `presentation/`.
- **Normalize user input at the validation boundary, not scattered
  later**: trim strings, uppercase tickers, etc. inside the use case's
  `execute()`, once, right after validation passes — see
  `AddStockHoldingUseCase` for the pattern.
- **Security-sensitive behavior stays in the repository implementation,
  not the use case**, so it can't be bypassed by a future caller. Example:
  `FirebaseAuthRepositoryImpl.login()`/`sendPasswordReset()` deliberately
  return the same generic message/success for "wrong password" and "no
  such account" (user-enumeration prevention, per Sprint 1 test case
  TC006) — that rule lives in the repository, not in `LoginUseCase`.
- **No secrets in source, ever.** API keys come from `local.properties`
  via a Gradle `buildConfigField` (see `FINNHUB_API_KEY` in
  `app/build.gradle.kts`) or from `google-services.json` (gitignored).
  This was Part One's `security-reviewer` finding #1 and it must never
  regress.
- **Test doubles, not a mocking framework.** There's no Mockito/MockK
  dependency in this project — write a small hand-written `Fake*`
  implementing the real interface (see `FakeAuthRepository`,
  `FakePortfolioRepository`, `FakeStockPriceRepository`) with public
  fields the test sets directly (`errorToReturn`, `holdingsToReturn`,
  etc.) and `*Called`/`last*` fields a test can assert against.
- **Known inconsistency, not yet cleaned up:** `AuthRepository` still
  uses `AuthCallback<T>`, while `PortfolioRepository` and
  `StockPriceRepository` use the newer, non-auth-specific
  `ResultCallback<T>` (added in Sprint 2 specifically so a non-auth
  interface didn't have to misuse `AuthCallback`). They're structurally
  identical. A future cleanup could collapse them into one interface and
  update `AuthRepository` + everything that implements/consumes it — not
  done yet because it wasn't in scope for any specific story. Don't
  silently "fix" this as a side effect of an unrelated change; call it
  out and do it as its own deliberate commit if asked to.
- **Resolved (was a known gap):** Firestore ran on open "test mode"
  rules through Sprint 2; closed 2026-10-04 with rules restricting
  `users/{uid}/holdings` (and the parent `users/{uid}` doc) to
  `request.auth.uid == userId`. See `references/decision-log.md`. If
  you're adding a new top-level collection, give it the same
  owner-scoped rule — don't assume Firestore is still open.

## Definition of done

A story/task isn't done until all of these hold, not just "the feature
works":

1. **Domain logic has zero Android/Firebase imports** and follows the
   UseCase convention above.
2. **Unit tests exist** for every new use case, using a hand-written Fake
   (not a live Firebase call) — cover the validation failure paths, not
   just the happy path.
3. **Verified against a real build**, not just hand-traced logic, before
   it's considered safe to merge. Hand-tracing (reading the test against
   the actual source line-by-line) is an acceptable stand-in when a real
   Gradle/emulator run genuinely isn't available, but it is a
   stand-in — say so explicitly rather than presenting it as equivalent
   to an executed test run. Prefer an actual `Compile All Sources` /
   `assembleDebug` run whenever possible (Sprint 2's real build caught a
   bug — an invalid `--` inside an XML comment — that hand-tracing never
   would have).
4. **No secrets committed** — check `git status`/the diff for
   `google-services.json`, `local.properties`, or a literal API key
   string before committing.
5. **New Activities are registered in `AndroidManifest.xml`.**
6. **Went through a PR, reviewed before merge, merged by a human**, not
   pushed straight to `main` (branch protection enforces this anyway,
   but the review should actually happen, not just be a formality).
7. **This file and the project's running notes are updated** if the
   change adds a new convention, a new known gap, or changes the
   architecture in a way a future session needs to know about.

## What NOT to do

- Don't add a mocking framework dependency — stay consistent with the
  hand-written Fake pattern already established.
- Don't let an Activity call Firebase/Finnhub directly — always through a
  use case and a repository interface.
- Don't invent a second validation style — copy the existing
  validate-then-delegate pattern instead of writing something new.
- Don't commit `app/google-services.json` or add a real API key to
  `local.properties.example` or any tracked file.
