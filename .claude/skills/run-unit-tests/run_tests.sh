#!/usr/bin/env bash
# PortfolioIQ: run every automated test and print one summary block.
# Used by the run-unit-tests skill. Works in Git Bash on Windows (what
# Claude Code uses there), macOS and Linux.
set -u

ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$ROOT" || { echo "Cannot open the repository root."; exit 2; }

# Gradle needs Java. Android Studio ships its own (jbr), so use it when
# JAVA_HOME isn't set.
if [ -z "${JAVA_HOME:-}" ]; then
    for d in "/c/Program Files/Android/Android Studio/jbr" \
             "$HOME/AppData/Local/Programs/Android Studio/jbr" \
             "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
             "/opt/android-studio/jbr"; do
        if [ -d "$d" ]; then export JAVA_HOME="$d"; break; fi
    done
fi

# ---- 1. Android unit tests -------------------------------------------------
echo "== 1/2 Android unit tests (gradlew :app:testDebugUnitTest) =="
RESULTS="app/build/test-results/testDebugUnitTest"
rm -rf "$RESULTS"   # never count an earlier run's results as this run's
bash ./gradlew :app:testDebugUnitTest --console=plain -q
GRADLE_EXIT=$?

# value of attribute $2 in the <testsuite ...> header $1, e.g. tests="12"
attr() { printf '%s' "$1" | grep -o " $2=\"[0-9]*\"" | grep -o '[0-9][0-9]*' | head -1; }

tests=0; failures=0; errors=0; skipped=0; files=0
for f in "$RESULTS"/TEST-*.xml; do
    [ -f "$f" ] || continue
    files=$((files + 1))
    head=$(grep -m1 -o '<testsuite [^>]*' "$f")
    v=$(attr "$head" tests);    tests=$((tests + ${v:-0}))
    v=$(attr "$head" failures); failures=$((failures + ${v:-0}))
    v=$(attr "$head" errors);   errors=$((errors + ${v:-0}))
    v=$(attr "$head" skipped);  skipped=$((skipped + ${v:-0}))
done

if [ "$files" -eq 0 ]; then
    ANDROID_LINE="Android unit tests: DID NOT RUN (gradle exit $GRADLE_EXIT, no result files)"
    ANDROID_OK=0
else
    ANDROID_LINE="Android unit tests: $tests run, $failures failed, $errors errors, $skipped skipped (gradle exit $GRADLE_EXIT)"
    if [ "$GRADLE_EXIT" -eq 0 ] && [ "$failures" -eq 0 ] && [ "$errors" -eq 0 ]; then ANDROID_OK=1; else ANDROID_OK=0; fi
    if [ "$failures" -gt 0 ] || [ "$errors" -gt 0 ]; then
        echo "Failing tests:"
        grep -l -E '<failure|<error' "$RESULTS"/TEST-*.xml 2>/dev/null | while read -r f; do
            echo "  in $(basename "$f" .xml | sed 's/^TEST-//')"
        done
    fi
fi

# ---- 2. AI server tests ----------------------------------------------------
echo "== 2/2 AI server tests (pytest, fake AI mode) =="
PY=""
SERVER_SKIPPED=0
for p in "$ROOT/ai-server/.venv/Scripts/python.exe" "$ROOT/ai-server/.venv/bin/python"; do
    if [ -x "$p" ] || [ -f "$p" ]; then PY="$p"; break; fi
done
if [ -z "$PY" ]; then
    SERVER_LINE="AI server tests: SKIPPED (ai-server/.venv not found: run ai-server/setup_server.bat first)"
    SERVER_OK=1
    SERVER_SKIPPED=1
else
    PYTEST_OUT=$(cd "$ROOT/ai-server" && "$PY" -m pytest -q 2>&1)
    PYTEST_EXIT=$?
    echo "$PYTEST_OUT" | tail -15
    SERVER_LINE="AI server tests: $(echo "$PYTEST_OUT" | grep -E '[0-9]+ (passed|failed)' | tail -1) (pytest exit $PYTEST_EXIT)"
    if [ "$PYTEST_EXIT" -eq 0 ]; then SERVER_OK=1; else SERVER_OK=0; fi
fi

# ---- Summary ---------------------------------------------------------------
echo
echo "== Summary =="
echo "$ANDROID_LINE"
echo "$SERVER_LINE"
if [ "$ANDROID_OK" -eq 1 ] && [ "$SERVER_OK" -eq 1 ]; then
    if [ "$SERVER_SKIPPED" -eq 1 ]; then
        echo "RESULT: PASS (ai-server tests SKIPPED, only the Android tests ran)"
    else
        echo "RESULT: PASS"
    fi
    exit 0
fi
echo "RESULT: FAIL"
exit 1
