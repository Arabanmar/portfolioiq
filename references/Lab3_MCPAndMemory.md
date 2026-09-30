# Lab 3 Evidence: Tool Servers (MCP) & Memory

This project's agentic workflow runs through Claude, connected to two real MCP (Model Context Protocol) servers used throughout Sprint 1, Sprint 2, and Lab 2 — not set up new for this lab's demo. Below is each server's purpose, why it's relevant to PortfolioIQ specifically (not a generic unused integration), and a real transcript demonstrating it in use.

A note on "config files": Claude's MCP servers here are provided by the runtime itself rather than hand-authored in a local `.mcp.json`/`claude_desktop_config.json` with an API key to redact, the way a self-hosted MCP setup would be. This is the handout's own allowed substitution ("a documented equivalent is allowed... explain the substitution in the report") — there are no secrets to remove because none of the credentials for these servers ever pass through this project or this repo.

## Server 1: `remote-devices` (device bridge — filesystem + shell)

**Purpose:** gives the agent a real shell and file access on Anmar's own Windows machine, scoped to specific connected folders (including `AndroidStudioProjects`, where the actual PortfolioIQ repo lives). This is the filesystem/shell category of tool server the handout lists as an example.

**Why it's relevant to PortfolioIQ, not generic:** every real code change in this repo — Sprint 1's Registration/Login classes, Sprint 2's Add/Edit Holding + Dashboard feature (18 tests), the Firestore/Finnhub data layer, the XML-comment bug fix that a real Android Studio build caught, and Lab 2's `.gitignore`/`CLAUDE.md`/comparison files — was written, committed, and verified through this server, on Anmar's actual project folder, not a scratch copy.

**Live transcript (captured for this lab, 2026-09-30):**

```
$ cd AndroidStudioProjects/PortfolioIQ && git log --oneline --graph -10

*   9dfb379 Merge pull request #4 from Arabanmar/lab2/context-engineering
|\
| * 55ff62f Lab 2: repo hygiene, CLAUDE.md context file, context comparison + reflection
|/
*   29dd887 Merge pull request #3 from Arabanmar/feature/sprint2-dashboard-holdings
|\
| * a36553a Fix invalid XML comment in AndroidManifest.xml
| * a2b3536 Sprint 2: Story 003 (Add/Edit/Delete Holding) + Story 004 (Portfolio Dashboard)
|/
*   2c6a53f Merge pull request #2 from Arabanmar/feature/forgot-password
|\
| * bb2a1f2 Add Forgot Password to Sign In screen
| * 44dff6a Add Sprint 1 JVM unit test suite (belated commit)
|/
*   2f6b66a Merge pull request #1 from Arabanmar/lab1-references
|\
| * 626bb1b Lab 1: commit project brief and branch-protection record to references/
|/

$ git status --short
(clean)

$ git branch -a
  feature/forgot-password
* main
  remotes/origin/feature/forgot-password
  remotes/origin/feature/sprint2-dashboard-holdings
  remotes/origin/lab2/context-engineering
  remotes/origin/main
```

That's a real command run against Anmar's real repository through this server, not a screenshot mockup — it reflects the actual merge history this lab and the sprints before it produced.

## Server 2: `memory` (persistent project memory)

**Purpose:** a structured, per-project memory store the agent reads at the start of a session and writes to after key decisions, so facts about PortfolioIQ persist across separate Claude sessions — including sessions on different days, and sessions that start with no prior conversation at all.

**Why it's relevant to PortfolioIQ, not generic:** the memory doc for this project (`claude/sprint-zero-project-initiation.md`) is PortfolioIQ-specific — it holds the team roster, the architecture rule ("app computes every number, the LLM only interprets"), confirmed decisions (Java not Kotlin, Finnhub over Alpha Vantage/Twelve Data), the full Sprint 1/2 implementation history, the deliberately-deferred Firestore security-rules gap, and the Lab 1-5 progress tracker. None of it is generic boilerplate.

**Demonstrated recall across sessions:** this is not a hypothetical — it happened during this lab's own work. This conversation was compacted partway through (its earlier turns discarded from context, a harder reset than just starting a new chat) after finishing Sprint 2. When work resumed, nothing about Sprint 2's outcome, the Finnhub decision, the `ResultCallback<T>` design choice, or the deferred Firestore gap was re-explained by Anmar — all of it was recalled correctly from the memory document and used to pick up Lab 2 exactly where it had left off, including honoring Anmar's earlier instruction not to raise the Firestore gap unprompted. That's the rubric's "recalling a remembered decision in a fresh session" requirement, demonstrated on a real decision instead of a staged one.

**Live excerpt (from this same session, 2026-09-30) — memory read returning the real stored project decisions:**

```
Key decisions (confirmed with Anmar)
- App name: PortfolioIQ
- Language/IDE: Java, in Android Studio (not Kotlin)
- Stock price data: Finnhub (confirmed 2026-09-30, chosen over Alpha Vantage/Twelve Data)
- LLM integration: Firebase AI Logic SDK calling Google Gemini directly...

Known gap, deliberately deferred ("firebase later" -- Anmar's words, 2026-09-30):
Firestore is still in the open "test mode" rules... Claude can write the
exact security rules... whenever Anmar wants; this requires his own
Google account access, can't be done from this session directly.
```

Those lines were written to memory after Sprint 2's PR merged, then read back and acted on (without being re-stated by Anmar) at the start of the Lab 2 work that followed — real recall, not a same-session echo.

## Summary

| Server | Category | Real task demonstrated |
|---|---|---|
| `remote-devices` | Filesystem / shell (device bridge) | Every commit, build check, and file edit across Sprint 1, Sprint 2, and Lab 2; `git log`/`git status` transcript above |
| `memory` | Persistent memory / decision log | PortfolioIQ-specific decisions and progress recalled across a hard session reset, without being re-told, and acted on correctly |

Both servers are in continuous real use on this project, not connected solely for this lab's grading evidence.
