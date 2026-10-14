# Lab 4: Custom Agents & Skills

Tool: Claude Code (the tool registered in Lab 1). Agents live in
`.claude/agents/`, skills in `.claude/skills/`. Both are committed, so every
team member's Claude Code session loads the same ones.

| Name | Kind | Job | What limits it |
|---|---|---|---|
| `code-reviewer` | Agent | Reviews changed files against CLAUDE.md and reports findings | Tool list: Read, Grep, Glob only. It cannot edit. |
| `test-writer` | Agent | Writes JUnit/pytest tests, runs them, reports exact counts | A rule in its prompt (test folders only). Its tools can write anywhere. |
| `new-use-case` | Skill (`/new-use-case`) | Adds a use case, repository contract, data class, fake and tests in the same order every time | Plan first, ask when unsure |
| `run-unit-tests` | Skill (`/run-unit-tests`) | Runs every automated test and prints one summary block | `allowed-tools` lets it run only its own script |

## Design rationale

### code-reviewer (agent)

- **Why this role:** until now every change was checked by hand against
  CLAUDE.md, and the same problems kept coming back (a missing `userId`, a
  mutable model, a test that can never fail). A reviewer with a fixed list
  makes that a habit.
- **Limit by tools, not by asking:** its `tools:` line gives it only Read,
  Grep and Glob. It cannot edit even when told to. We tested this (see
  evidence step 5). A reviewer that also fixes things is no longer a second
  pair of eyes.
- **Checklist tied to our rules:** each check points at a CLAUDE.md section
  (architecture, UseCase convention, security, tests, UI), so every finding
  traces back to a written rule.
- **Named test evasions:** it lists the ways a test can pass without testing
  anything (no assertion, `@Ignore`, a loosened assertion, asserting the
  fake's own value). "Check the tests are good" is too vague to act on.
- **Says what it did not check:** it cannot build or run tests, so it has to
  say so. An APPROVE never means "it builds".

### test-writer (agent)

- **Why this role:** tests are where shortcuts hide, so writing them is a
  separate job from writing the feature.
- **Scope rule:** its prompt allows it to write only under `app/src/test/`
  and `ai-server/tests/`. If a test finds a bug it must report it, not edit
  production code or bend the test. This is a prompt rule only: it needs
  Write, Edit and Bash to do its job, and those tools are not limited by
  path. The code reviewer pointed this out (finding 5). In practice we check
  `git status` after it runs; in this lab it changed one test file only.
- **House style:** JUnit 4, hand-written fakes that record what they were
  called with, one test per validation branch in the order the code checks
  them. This matches the existing tests.
- **Numbers, not belief:** it must finish by running the `run-unit-tests`
  script and quoting the summary.

### new-use-case (skill)

- **Why a skill:** every feature repeats the same pieces (use case,
  repository contract, data implementation, fake, test). The skill makes the
  agent do them in the same order and shape every time.
- **Plan before code:** it must show the inputs, the validations with their
  exact messages and the repository choice before writing files, and ask the
  human when CLAUDE.md doesn't settle something.
- **Template file:** `template.md` holds skeletons for the use case,
  repository contract, fake and test, modelled on `AddStockHoldingUseCase`.
  It has no skeleton for the data-layer class; the skill only says what goes
  there (the only place for Firebase or HTTP code, raw errors turned into
  messages a user can read).
- **Hand-offs:** it can pass the test step to `test-writer`, runs
  `/run-unit-tests` at the end, and then suggests the `code-reviewer` agent,
  so one command covers the whole small loop.

### run-unit-tests (skill)

- **Why a skill:** "do the tests pass?" was answered differently each time
  (reading the code, a javac run, Android Studio). One script answers it the
  same way for everyone.
- **What it runs:** `gradlew :app:testDebugUnitTest` for the Android unit
  tests and `pytest` for the AI server (fake AI mode, no key used).
- **A number you can repeat:** it counts results from Gradle's own XML
  reports and deletes the previous run's report folder first, so an old green
  run is never reported as a new one.
- **Three endings:** `RESULT: PASS`, `RESULT: FAIL`, or
  `RESULT: PASS (ai-server tests SKIPPED, only the Android tests ran)` when the
  Python environment isn't set up. The third is not a full pass. It was added
  after the code review (finding 2).
- **Fails loudly:** if Gradle cannot start (for example no Java), it prints
  "DID NOT RUN" instead of reporting zero failures.

## Evidence of use on real project work

Feature: the dashboard greets the signed-in user by the username saved at
sign-up ("Welcome, <username>", or "Welcome back" if there is none). This
finishes the Sprint 3 username work from the doctor's review.

Full transcript: `references/Lab4_ClaudeCodeSession.md` (exported from
Claude Code's own session log, 2026-10-14, 01:07 to 01:33 UTC). Branch:
`lab4/agents-and-skills`.

| # | Time (UTC) | What we asked | What happened |
|---|---|---|---|
| 1 | 01:08 | `/new-use-case GetUserProfileUseCase "..."` | Read the template and existing code first, searched for an existing profile reader, then created `UserProfileRepository`, `GetUserProfileUseCase`, `FirestoreUserProfileRepositoryImpl`, a fake and 8 tests, and wired the dashboard. Ran `/run-unit-tests`: 73 Android + 12 pytest, PASS. |
| 2 | 01:13 | `test-writer` agent: check and complete the tests | Found gaps (empty and tab-only ids and usernames, double callbacks not detectable). Added 10 tests, 18 in the class, only the test file changed, no bug found. The main session re-ran the suite itself instead of trusting the report: 83 + 12, PASS. |
| 3 | 01:16 | `/run-unit-tests` | `83 run, 0 failed, 0 errors, 0 skipped` and `12 passed`, `RESULT: PASS`. |
| 4 | 01:17 | `code-reviewer` agent: review every file changed vs `main` | 16 files reviewed. No blocking issues, 6 should-fix, several nits. Most important: `getString()` can throw on a wrong field type and would crash the dashboard; the script printed a bare PASS when the AI-server tests were skipped; no `.gitattributes`; our own docs overstated the test-writer limit. |
| 5 | 01:23 | Asked the reviewer to fix a finding itself | It said it cannot: it has Read, Grep and Glob only. It sent back the fix in words for someone with edit access. The tool limit held. |
| 6 | 01:23 | Commit the feature | `c140d3e` "Dashboard: greet the signed-in user by username" (7 files). |
| 7 | 01:25 | Fix findings 1 to 4, run tests, commit | try/catch around `getString`, the SKIPPED result line, `.gitattributes` with `*.sh text eol=lf`, SKILL.md wording. Tested the skipped path with a copy of the script. 83 + 12, PASS. Commit `329359d`. |
| 8 | 01:27 | `code-reviewer` agent: review only the fix commit | Items 1, 2 and 4 fixed, item 3 only partly: `gradlew` has no `.sh` extension, so it was not covered. Also flagged this doc's outdated line about the script's output. |
| 9 | 01:30 | Apply the follow-ups, run tests, commit | Added `gradlew text eol=lf` and `*.bat text eol=crlf`, renamed the failure header. 83 + 12, PASS. Commit `eb04d95`. Branch pushed. |

### What the agents caught that we would have missed

- The `getString()` crash risk was in code Claude Code had just written, with
  a wrong comment explaining why it was safe. The reviewer caught it.
- The review of the fix commit caught that the first fix was incomplete
  (`gradlew`). One review pass would not have been enough.
- The reviewer caught our own documentation overstating a limit. The table at
  the top of this file now says which limits come from tools and which are
  only prompt rules.

### Still not verified

- The greeting has not been seen on a phone or emulator yet. Gradle compiled
  everything and the unit tests ran, but the app itself has not been run with
  this change.
- The new catch branch in `FirestoreUserProfileRepositoryImpl` has no test,
  because the class needs Firebase. Manual check: set `username` to a number
  in the Firestore console and open the dashboard. It should show
  "Welcome back".
- The reviewer noted that about 5 of the 18 tests overlap with others, so the
  count is a little inflated.
