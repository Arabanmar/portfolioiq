# Lab 4: Custom Agents & Skills

Tool: Claude Code (the tool registered in Lab 1). Agents live in
`.claude/agents/`, skills in `.claude/skills/`, both committed so every team
member's Claude Code session loads the same ones.

| Name | Kind | One-line job | Can write? |
|---|---|---|---|
| `code-reviewer` | Agent | Reviews changed files against CLAUDE.md and reports findings | No: Read, Grep, Glob only |
| `test-writer` | Agent | Writes JUnit/pytest tests, runs them, reports exact counts | Only test folders |
| `new-use-case` | Skill (`/new-use-case`) | Scaffolds a use case, repository contract, fake and tests the same way every time | Through the main session |
| `run-unit-tests` | Skill (`/run-unit-tests`) | Runs every automated test and prints one reproducible summary | No (runs tests only) |

## Design rationale

### code-reviewer (agent)

- **Why this role:** every change so far was reviewed by hand against
  CLAUDE.md, and the same issues kept coming back (a missing `userId`, a
  mutable model, a test that never fails). A reviewer that always checks the
  same list turns that into a habit instead of a memory test.
- **Capability, not instruction:** its `tools:` line gives it only Read, Grep
  and Glob. It cannot edit a file even if asked to, which is a stronger
  guarantee than a sentence saying "please don't edit". A reviewer that also
  fixes things stops being a second pair of eyes.
- **Checklist tied to our rules:** each check points at a CLAUDE.md section
  (architecture, UseCase convention, security, tests, UI), so a finding is
  traceable to a written rule rather than taste.
- **Named evasions:** it lists the concrete ways a test can pass without
  testing anything (no assertion, `@Ignore`, loosened assertion, asserting
  the fake's own value), because "check the tests are good" is too vague to act on.
- **Honest output:** it must say what it could not check (it cannot build or
  run tests), so an APPROVE is never read as "it builds".

### test-writer (agent)

- **Why this role:** tests are where shortcuts hide, so writing them is a
  separate job from writing the feature.
- **Scope limit:** it may write only under `app/src/test/` and
  `ai-server/tests/`. If a test exposes a bug, it reports it instead of
  editing production code or bending the test, so a green suite always
  means the code is right, not that the test was adjusted.
- **House style:** JUnit 4, hand-written fakes that record what they were
  called with, one test per validation branch in the order the code checks
  them, mapped to test-case ids. This matches the 65 existing tests.
- **Evidence, not belief:** it must finish by running `/run-unit-tests` and
  quoting the numbers.

### new-use-case (skill)

- **Why a skill:** adding a feature always repeats the same five pieces
  (use case, repository contract, data implementation, fake, test). A skill
  makes the agent do them in the same order, with the same shape, every time.
- **Plan before code:** it must show the inputs, validations with exact
  messages, and repository choice before writing files, and ask the human
  when CLAUDE.md doesn't settle something (for example a Firestore field
  name). "Never guess: verify or ask."
- **Template file:** `template.md` holds skeletons copied from
  `AddStockHoldingUseCase`, so new code matches existing code instead of the
  model's generic idea of Java.
- **Built-in hand-offs:** it hands tests to `test-writer`, runs
  `run-unit-tests`, then suggests `code-reviewer`, so one command walks the
  whole small loop.

### run-unit-tests (skill)

- **Why a skill:** "do the tests pass?" was being answered differently each
  time (hand-tracing, a javac run, Android Studio). One script answers it
  the same way for everyone.
- **What it runs:** `gradlew :app:testDebugUnitTest` for the Android unit
  tests and `pytest` for the AI server (fake AI mode, no key used).
- **Reproducible number:** it counts results from Gradle's own XML reports
  and deletes the previous run's report folder first, so an old green run
  can never be reported as a new one. It ends with `RESULT: PASS` or `FAIL`.
- **Fails loudly:** if Gradle cannot start (for example no Java), it says
  "DID NOT RUN" rather than reporting zero failures.

## Evidence of use on real project work

Feature: the dashboard greets the signed-in user by the username saved at
sign-up (finishing the Sprint 3 username work).

1. `/new-use-case GetUserProfileUseCase "..."`: _screenshot and summary to add_
2. `test-writer` agent writes and runs the tests: _screenshot to add_
3. `/run-unit-tests`: _summary block to add_
4. `code-reviewer` agent reviews the change: _findings to add_
5. Pull request: _link to add_
