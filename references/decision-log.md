# PortfolioIQ — Decision Log

A dated record of project decisions, kept in-repo so they're independently verifiable rather than resting only on an AI assistant's own memory of a conversation. Each entry is confirmed by a source outside the conversation itself: a commit date, a platform record (Firebase Console's publish history), or git history, as noted in its own "Verified by" column.

| Date | Decision | Verified by |
|---|---|---|
| 2026-09-26 | Lab 1 scope agreed: Claude Code as the agentic tool for Part Two, clean-architecture split adapted from the `android-clean-architecture` skill. | `references/PortfolioIQ_Lab1_ProjectBrief.pdf`, commit history on `main`. |
| 2026-09-30 | Live stock price data: **Finnhub** chosen over Alpha Vantage / Twelve Data (free tier, simplest per-ticker quote endpoint for Sprint 2's scope). | Sprint 2 code (`FinnhubStockPriceRepository`), merged in PR #3. |
| 2026-09-30 | Firestore security rules intentionally left in open "test mode" for the duration of active development ("firebase later" — deferred, not forgotten). Documented as a known, open gap in `CLAUDE.md` rather than left unstated. | `CLAUDE.md`, "Coding standards" section, merged in PR #4 (`9dfb379`). |
| 2026-10-04 | Firestore security rules closed: real `allow read, write` rules scoped to `request.auth.uid == userId` published to the production Firebase project, replacing test mode. | Firebase Console → Firestore → Rules → publish history (dated 2026-10-04). |
| 2026-10-04 | `FINNHUB_API_KEY` regenerated and distributed to the team via `local.properties` (per-developer, gitignored — never committed) after the original key was found missing from two developers' local setups. | Team message thread; `local.properties` is listed in `.gitignore`, and `git log --all -- local.properties` returns no results — confirmed absent from the full git history, not just assumed from the ignore rule. |

## Why this file exists

The project's Lab 3 submission documents an AI memory tool (Claude's persistent project memory) that recalls decisions like the Finnhub choice above across separate work sessions without being re-told. That claim is real, but a transcript of an AI's own memory is hard for a third party to independently verify. This file holds the same kind of decisions, timestamped and committed to version control, so the record stands on its own regardless of which tool was used to help write the code around it. It has grown beyond the original Lab 3 scope as new decisions were made (Firestore rules, the Finnhub key rotation), and is kept up to date rather than frozen at submission time.
