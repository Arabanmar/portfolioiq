---
name: new-use-case
description: Scaffold a new PortfolioIQ use case end to end (domain use case, repository contract, data-layer implementation, hand-written fake and JUnit tests) following CLAUDE.md's UseCase convention. Use when adding any new user action or business rule to the Android app.
argument-hint: <UseCaseName> "<what the user action does>"
---

# New use case

Builds one PortfolioIQ use case the same way every time, so a new feature
looks exactly like `AddStockHoldingUseCase` and its tests.

Arguments: `$0` is the class name (must end in `UseCase`), `$1` is a one-line
description of the user action. If either is missing or unclear, ask before
writing anything.

## Step 1: read before writing

Read these, in full:
- `CLAUDE.md` (sections "Architecture summary", "UseCase convention", "Coding standards", "Definition of done")
- `app/src/main/java/com/portfolioiq/domain/usecase/AddStockHoldingUseCase.java` (the template)
- `app/src/test/java/com/portfolioiq/domain/usecase/AddStockHoldingUseCaseTest.java`
- every repository interface in `app/src/main/java/com/portfolioiq/domain/repository/`

## Step 2: write the plan first and show it

Before creating files, write down and show:
1. **Inputs**: the raw strings the screen will pass in, plus `userId` if the data belongs to a user.
2. **Output**: the `ResultCallback<T>` type.
3. **Validations, in order**, each with its exact user-facing message.
4. **Repository**: reuse an existing interface method, add a method to an
   existing interface, or create a new interface. User-scoped methods take
   `userId` first. New interfaces use `ResultCallback<T>`, not `AuthCallback`.
5. **Files** you will create or change.

If the plan needs a decision the code and CLAUDE.md don't settle (for
example a Firestore field name or a new collection), ask the human. Never guess.

## Step 3: create the files

Use the skeletons in [template.md](template.md).
- `domain/usecase/$0.java`: validate in order, return on the first failure, then delegate.
- `domain/repository/...`: the interface change, if any. No Android or Firebase types.
- `data/repository/<Tech><Interface>Impl.java`: the only place Firebase or HTTP code goes. Translate raw exceptions into user-safe messages.
- `app/src/test/.../domain/repository/Fake<Interface>.java`: create or extend the hand-written fake.
- `app/src/test/.../domain/usecase/$0Test.java`: one test per validation, the happy path, and the repository error. If the `test-writer` agent is available, hand this step to it.

Wire the use case into an Activity only when the task asks for UI: the
Activity builds the repository and use case in `onCreate()` and only
displays results. Register any new Activity in `AndroidManifest.xml` and
use `piq_*` colors in its layout.

## Step 4: verify, then report

1. Run the `run-unit-tests` skill and quote its summary lines exactly.
2. Check the Definition of done in CLAUDE.md item by item, and say which
   ones still need a human (a real Android Studio build, a PR review).
3. Finish with: files created or changed, the validation list, the test
   count, and anything left open. Then suggest running the `code-reviewer`
   agent on those files.
