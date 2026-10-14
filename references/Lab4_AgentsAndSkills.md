# Lab 4: Custom Agents and Skills

We used Claude Code, the same tool we picked in Lab 1. The agents are in the
.claude/agents folder and the skills are in the .claude/skills folder. They
are committed to the repo, so everyone on the team gets the same ones.

## What we made

- code-reviewer (agent): checks changed files against our rules in
  CLAUDE.md and lists what is wrong. It can only read files, so it cannot
  change anything.
- test-writer (agent): writes unit tests, runs them and reports the numbers.
  Its instructions say it may only touch the test folders, but it has write
  tools, so this is a rule it follows, not something it is blocked from.
- new-use-case (skill): adds a new use case the same way every time. It plans
  first, then makes the use case, the repository, the data class, a fake and
  the tests.
- run-unit-tests (skill): runs all our tests (Android and the AI server) and
  prints one short summary.

## Why we made them this way

### code-reviewer

- When we reviewed code by hand, the same mistakes kept coming back, like a
  missing user id or a test that could never fail. A fixed checklist catches
  them every time.
- We gave it read tools only. A reviewer that also fixes the code is not
  really a second check anymore. We tested this: when we asked it to fix
  something, it said it could not.
- Every check points to a rule in CLAUDE.md, so each finding has a reason.
- It lists the common ways a test can pass without testing anything, like a
  test with no assert or a test that only checks the fake's own value.
- It has to say what it did not check. It cannot build the app or run tests,
  so its approval does not mean the app builds.

### test-writer

- Tests are where shortcuts hide, so writing them is a separate job from
  writing the feature.
- If a test finds a bug, it reports it. It does not change the app code or
  weaken the test to make it pass.
- The code reviewer pointed out that the "test folders only" limit is only an
  instruction. So after it runs we check git status. In this lab it changed
  one test file only.
- It follows our test style: JUnit 4, hand-written fakes, one test for each
  check in the code.
- It has to finish by running the tests and showing the real numbers.

### new-use-case

- Every feature needs the same pieces: use case, repository, data class, fake
  and tests. The skill makes them in the same order and style every time.
- It has to show a plan before writing code, and ask us when CLAUDE.md does
  not answer something, instead of guessing.
- It comes with a template file based on our existing AddStockHoldingUseCase.
- At the end it runs the tests and suggests a code review, so one command
  covers the whole small loop.

### run-unit-tests

- Before this, "do the tests pass?" was answered in a different way each time.
  Now one script answers it the same way for everyone.
- It runs the Android unit tests with Gradle and the AI server tests with
  pytest. The AI server tests use fake mode, so no API key is needed.
- It deletes old results before running, so an old passing run is never
  reported as a new one.
- It ends with one of three results: PASS, FAIL, or PASS with the AI server
  tests skipped (when Python is not set up). The third one is not a full pass.
- If Gradle cannot start, it says the tests did not run instead of saying
  zero failed.

## How we used them on real work

The feature: the dashboard now says "Welcome, <username>" using the name
saved at sign-up, or "Welcome back" if there is no name. This finishes the
username change the doctor asked for in Sprint 3.

The full session is in references/Lab4_ClaudeCodeSession.md, taken from
Claude Code's own log (14 October 2026).

1. We ran /new-use-case for GetUserProfileUseCase. It read our existing code
   first, then made the use case, repository, Firestore class, a fake and 8
   tests, and added the greeting to the dashboard. Tests: 73 Android and 12
   AI server, all passed.
2. We asked the test-writer agent to check the tests. It found missing cases
   (empty or blank names and ids, and a callback being called twice) and
   added 10 tests, 18 in total. It only changed the test file. Claude Code
   then ran the tests again itself instead of trusting the report.
3. We ran /run-unit-tests: 83 Android tests and 12 AI server tests, 0 failed.
4. We asked the code-reviewer agent to review all 16 changed files. Nothing
   was blocking, but it found 6 things to fix. The most important one: reading
   the username could crash the dashboard if the field had the wrong type.
5. We asked the reviewer to fix that itself. It could not, because it only
   has read tools. It explained the fix instead.
6. The feature was committed (c140d3e).
7. Claude Code fixed the first 4 findings, ran the tests again (all passed)
   and committed the fixes (329359d).
8. We asked the reviewer to check only that fix. It found one fix was not
   complete: the gradlew file was not covered by the line ending rule.
9. Claude Code fixed that, ran the tests again (all passed) and committed it
   (eb04d95).

## What the agents caught

- The crash risk was in code Claude Code had just written. The reviewer found
  it.
- The second review found that the first fix was not complete. One review was
  not enough.
- The reviewer also found that our own notes said test-writer was limited to
  test folders by its tools, which was wrong. We fixed the notes.

## Known gaps

- The new error handling in FirestoreUserProfileRepositoryImpl has no unit
  test, because that class needs Firebase. To check it by hand, set the
  username to a number in the Firestore console and open the dashboard. It
  should show "Welcome back".
- The reviewer said about 5 of the 18 tests repeat other tests, so the count
  is a little higher than it needs to be.
