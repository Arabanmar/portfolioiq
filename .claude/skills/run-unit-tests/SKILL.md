---
name: run-unit-tests
description: Run all of PortfolioIQ's automated tests (Android unit tests through Gradle, plus the ai-server pytest suite) and report the exact numbers. Use before saying any change is done, before opening a pull request, and whenever someone asks whether the tests pass.
allowed-tools: Bash(bash .claude/skills/run-unit-tests/run_tests.sh*)
---

# Run unit tests

"Done" means a number someone else can reproduce, not "I believe it works".
This skill produces that number the same way every time.

## Steps

1. From the repository root, run:

   ```
   bash .claude/skills/run-unit-tests/run_tests.sh
   ```

   It runs `gradlew :app:testDebugUnitTest` (the plain JVM unit tests under
   `app/src/test/`), counts the results from Gradle's XML reports, then runs
   `pytest` for `ai-server/` with its own Python environment (fake AI mode,
   no Gemini key used). It ends with a `== Summary ==` block and a
   `RESULT: PASS` or `RESULT: FAIL` line.

2. Report back:
   - the `== Summary ==` block, copied exactly;
   - for any failure, the failing test names the script lists and the first
     lines of the error;
   - any part that was skipped (for example the ai-server environment not
     set up yet) and what the human must do about it.

## Rules

- Never summarise the result as "all tests pass" without quoting the numbers.
- Never edit a test or production file from this skill. If something fails,
  report it; fixing it is a separate step.
- If Gradle cannot start (for example no Java found), say so plainly and stop.
  Do not count a test run that did not happen.
- The script deletes only Gradle's previous unit-test result folder
  (`app/build/test-results/testDebugUnitTest`) so old results are never
  counted as new ones.
