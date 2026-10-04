# Lab 2 Evidence: Context File Before/After Comparison

**Method:** The same task — "Write a `SellStockUseCase` for PortfolioIQ that lets a user sell part or all of a stock holding" — was given to two independent agent instances with no shared memory of each other or of this repository's real state. Neither instance was given file access; each produced a standalone Java class from the prompt text alone. This isolates the effect of the context file itself, not of re-reading the codebase.

- **Instance A (no context):** received only the task sentence above.
- **Instance B (with context):** received the task sentence plus the full text of this repository's `CLAUDE.md`.

Both outputs below are reproduced as generated, not edited or cleaned up afterward.

## Instance A — no context

```java
package com.portfolioiq.data.holding;

import java.math.BigDecimal;

public class SellStockUseCase {

    public interface Callback {
        void onResult(SellStockResult result);
    }

    public enum SellStockError {
        HOLDING_NOT_FOUND,
        INVALID_QUANTITY,
        INSUFFICIENT_SHARES
    }

    public static class SellStockResult {
        public final boolean success;
        public final SellStockError error;

        private SellStockResult(boolean success, SellStockError error) {
            this.success = success;
            this.error = error;
        }

        public static SellStockResult ok() {
            return new SellStockResult(true, null);
        }

        public static SellStockResult fail(SellStockError error) {
            return new SellStockResult(false, error);
        }
    }

    private final StockHoldingRepository repository;

    public SellStockUseCase(StockHoldingRepository repository) {
        this.repository = repository;
    }

    /**
     * Sells the given quantity of shares from a holding.
     */
    public void execute(String holdingId, BigDecimal quantity, Callback callback) {
        if (holdingId == null) {
            throw new IllegalArgumentException("holdingId is required");
        }
        StockHolding holding = repository.findById(holdingId);
        if (holding == null) {
            callback.onResult(SellStockResult.fail(SellStockError.HOLDING_NOT_FOUND));
            return;
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            callback.onResult(SellStockResult.fail(SellStockError.INVALID_QUANTITY));
            return;
        }
        if (quantity.compareTo(holding.getQuantity()) > 0) {
            callback.onResult(SellStockResult.fail(SellStockError.INSUFFICIENT_SHARES));
            return;
        }
        repository.reduceQuantity(holdingId, quantity);
        callback.onResult(SellStockResult.ok());
    }
}
```

Notes on what this gets wrong, checked against the real repo: `com.portfolioiq.data.holding` is not a real package — the real one is `com.portfolioiq.domain.usecase`. `StockHolding` and `StockHoldingRepository` are invented; the real classes are `com.portfolioiq.domain.model.StockHolding` and `com.portfolioiq.domain.repository.PortfolioRepository`, and `PortfolioRepository` has no `reduceQuantity`/`findById` methods — it has `getHoldings`/`addHolding`/`updateHolding`/`deleteHolding`. It invents its own `Callback`/`SellStockResult`/`SellStockError` hierarchy instead of using the project's real `ResultCallback<T>`. It uses `BigDecimal` where every real use case in the repo uses `double`. It throws `IllegalArgumentException` for a null ID instead of routing the problem through the callback, which is inconsistent with how every other use case in the repo reports errors (as a user-facing `onError(String)` message, never a thrown exception). The Javadoc is generic and has no Story reference.

## Instance B — with `CLAUDE.md` context

```java
package com.portfolioiq.domain.usecase;

import com.portfolioiq.domain.model.StockHolding;
import com.portfolioiq.domain.repository.PortfolioRepository;
import com.portfolioiq.domain.repository.ResultCallback;

/**
 * Story 003 (extension): sells part or all of an existing stock holding.
 *
 * Fetches the real holding first rather than trusting a caller-supplied
 * quantity, so the "insufficient shares" check is always against the
 * actual current state in Firestore, not a stale value the UI might be
 * holding. If the sale covers the full remaining quantity (within a
 * small epsilon, since quantities are doubles), the holding is deleted
 * instead of being updated to a zero-quantity row.
 */
public class SellStockUseCase {

    private static final double QUANTITY_EPSILON = 1e-6;

    private final PortfolioRepository portfolioRepository;

    public SellStockUseCase(PortfolioRepository portfolioRepository) {
        this.portfolioRepository = portfolioRepository;
    }

    public void execute(String holdingId, String quantityToSellText, ResultCallback<Void> callback) {
        if (holdingId == null || holdingId.trim().isEmpty()) {
            callback.onError("Select a holding to sell.");
            return;
        }

        final double quantityToSell;
        try {
            quantityToSell = Double.parseDouble(quantityToSellText.trim());
        } catch (NumberFormatException | NullPointerException e) {
            callback.onError("Enter a valid quantity.");
            return;
        }

        if (quantityToSell <= 0) {
            callback.onError("Quantity to sell must be greater than zero.");
            return;
        }

        portfolioRepository.getHoldings(new ResultCallback<java.util.List<StockHolding>>() {
            @Override
            public void onSuccess(java.util.List<StockHolding> holdings) {
                StockHolding holding = findById(holdings, holdingId);
                if (holding == null) {
                    callback.onError("That holding no longer exists.");
                    return;
                }
                if (quantityToSell > holding.getQuantity() + QUANTITY_EPSILON) {
                    callback.onError("You can't sell more shares than you currently hold.");
                    return;
                }
                if (Math.abs(quantityToSell - holding.getQuantity()) < QUANTITY_EPSILON) {
                    portfolioRepository.deleteHolding(holdingId, callback);
                } else {
                    StockHolding remaining = holding.withId(holding.getId());
                    remaining.setQuantity(holding.getQuantity() - quantityToSell);
                    portfolioRepository.updateHolding(remaining, callback);
                }
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private StockHolding findById(java.util.List<StockHolding> holdings, String id) {
        for (StockHolding h : holdings) {
            if (h.getId().equals(id)) {
                return h;
            }
        }
        return null;
    }
}
```

Checked against the real repo: package and all three imports (`StockHolding`, `PortfolioRepository`, `ResultCallback`) are the actual classes that exist. The `execute(String, String, ResultCallback<Void>)` signature matches the project's raw-string, validate-then-delegate convention used by `AddStockHoldingUseCase`/`EditStockHoldingUseCase`. Error messages are returned through the callback, never thrown, matching every other use case. `double` with an epsilon comparison is used instead of `BigDecimal`, matching `StockHolding`'s real field type. The Javadoc references the Story and explains a real design decision (fetch-then-compare against the live value) instead of restating the method signature in prose.

## What the comparison shows

The two outputs are not stylistic variations of the same design — they diverge on facts that a compiler would catch immediately: wrong package (`data.holding` vs. the real `domain.usecase`), wrong or nonexistent repository methods, a duplicated custom result/error type instead of the project's actual `ResultCallback<T>`, and a different numeric type (`BigDecimal` vs. the `double` every model in this repo actually uses). None of this is something a human reviewer would call a "style preference" — Instance A's code would not compile against this repository at all.

The only difference between the two runs was the presence of `CLAUDE.md`'s "Architecture summary," "UseCase convention," and "Coding standards" sections in the prompt. Those sections are specifically what supplied the real package names, the real callback interface, the raw-string/validate-then-delegate pattern, and the numeric-type convention — none of which are guessable from the task sentence alone, because "sell part of a holding" is generic enough to be implemented correctly in infinitely many ways that would still be wrong for this specific codebase.

## Compile check (real javac output, not reasoning about the code)

The "would not compile" claim above was, until now, a hand-traced judgment — a careful reading of the code against the real repository, not an actual build. This section replaces that judgment with a real compiler run against both classes, on a dedicated scratch branch (`lab2/compile-check-scratch`, not merged into `main`), and reports what actually happened — including a real compile error in Instance B that the original comparison did not catch.

**Method:** Both classes were added to the real repository, unmodified from the text above (only the top-level class renamed `SellStockUseCase` → `SellStockUseCaseInstanceA` / `SellStockUseCaseInstanceB` so each file could exist side by side without a naming collision; no other line was changed). Neither file imports any `android.*` class — the domain layer in this project is plain Java by design — so a direct `javac` compile against the project's real domain-layer sources is a faithful equivalent of `./gradlew compileDebugJavaWithJavac` for this specific question, and was used instead of the full Gradle build: the sandboxed device shell this project's agent runs in has no JDK compiler installed and no network path to Gradle's distribution server, so a full `assembleDebug` could not be run from there. The same source files were copied, unedited, into an environment with a real `javac` (OpenJDK 21) and compiled directly against this repository's actual `StockHolding.java`, `PortfolioRepository.java`, and `ResultCallback.java`.

**Instance A — compiled alone (it references no class that exists in the real repository):**

```
src/com/portfolioiq/data/holding/SellStockUseCaseInstanceA.java:35: error: cannot find symbol
    private final StockHoldingRepository repository;
                  ^
  symbol:   class StockHoldingRepository
  location: class SellStockUseCaseInstanceA
src/com/portfolioiq/data/holding/SellStockUseCaseInstanceA.java:37: error: cannot find symbol
    public SellStockUseCaseInstanceA(StockHoldingRepository repository) {
                                     ^
  symbol:   class StockHoldingRepository
  location: class SellStockUseCaseInstanceA
src/com/portfolioiq/data/holding/SellStockUseCaseInstanceA.java:48: error: cannot find symbol
        StockHolding holding = repository.findById(holdingId);
        ^
  symbol:   class StockHolding
  location: class SellStockUseCaseInstanceA
3 errors
```

Confirmed: does not compile, exactly as the original comparison claimed. `StockHoldingRepository` and this package's `StockHolding` are not real classes in this repository.

**Instance B — compiled against the real `StockHolding`, `PortfolioRepository`, and `ResultCallback`:**

```
src/com/portfolioiq/domain/usecase/SellStockUseCaseInstanceB.java:59: error: method deleteHolding in interface PortfolioRepository cannot be applied to given types;
                    portfolioRepository.deleteHolding(holdingId, callback);
                                       ^
  required: String,String,ResultCallback<Void>
  found:    String,ResultCallback<Void>
  reason: actual and formal argument lists differ in length
src/com/portfolioiq/domain/usecase/SellStockUseCaseInstanceB.java:62: error: cannot find symbol
                    remaining.setQuantity(holding.getQuantity() - quantityToSell);
                             ^
  symbol:   method setQuantity(double)
  location: variable remaining of type StockHolding
src/com/portfolioiq/domain/usecase/SellStockUseCaseInstanceB.java:63: error: method updateHolding in interface PortfolioRepository cannot be applied to given types;
                    portfolioRepository.updateHolding(remaining, callback);
                                       ^
  required: String,StockHolding,ResultCallback<Void>
  found:    StockHolding,ResultCallback<Void>
  reason: actual and formal argument lists differ in length
src/com/portfolioiq/domain/usecase/SellStockUseCaseInstanceB.java:46: error: method getHoldings in interface PortfolioRepository cannot be applied to given types;
        portfolioRepository.getHoldings(new ResultCallback<java.util.List<StockHolding>>() {
                           ^
  required: String,ResultCallback<List<StockHolding>>
  found:    <anonymous ResultCallback<List<StockHolding>>>
  reason: actual and formal argument lists differ in length
4 errors
```

**This is a real finding the original comparison missed: Instance B does not compile either.** All four errors come from the same root cause — `PortfolioRepository`'s real methods (`getHoldings`, `updateHolding`, `deleteHolding`) all take a `userId` as their first parameter, scoping every call to one signed-in user's data, and Instance B's generated code omits it on all three calls. The fourth error is separate: it calls `remaining.setQuantity(...)`, but the real `StockHolding` is an immutable value object with no setters at all — every field is `final` and only set through the constructor or `withId(...)`.

This does not reverse the comparison's conclusion — Instance B is still unambiguously closer to the real codebase (correct package, correct imports, correct callback interface, correct numeric type, correct validate-then-delegate convention) — but "closer" is not "compiles," and the earlier write-up overstated it by implying Instance B was build-correct without ever having tried to build it. A context file supplies the shape of the convention; it does not substitute for compiling against the actual interfaces, and multi-user-scoped repository methods and an immutable model are exactly the kind of signature detail that only shows up by trying to call them, not by reading `CLAUDE.md`'s prose.

The scratch branch and both compiler transcripts above are the full, unedited evidence; nothing here was summarized away.

## Re-run after the CLAUDE.md fix (2026-10-04)

The compile check above found two real gaps in `CLAUDE.md`'s UseCase convention section: it never stated that every `PortfolioRepository` method takes `userId` first, and never stated that `StockHolding` is immutable with no setters. Both were added to `CLAUDE.md` as two new bullets in that same section (see `CLAUDE.md` and the commit on this branch). To check whether that actually fixes anything, the same blind-generation method was re-run: a fresh agent instance, zero prior conversation history, no file access, given only the task sentence plus the full updated text of `CLAUDE.md` (the two new bullets included), asked to produce "Instance B (revised)." The agent was not told what the previous compile check found.

**Instance B (revised) — what changed:** it now passes `userId` as the first argument on every `getHoldings`/`updateHolding`/`deleteHolding` call, and builds a new `StockHolding` instance rather than calling a setter — both exactly matching the two new rules. Compiled as generated against this repository's real `StockHolding`, `PortfolioRepository`, and `ResultCallback` (after only a mechanical package-prefix correction — `domain.usecase` to `com.portfolioiq.domain.usecase` — a separate, already-documented limitation: `CLAUDE.md`'s architecture diagram shows the `domain/model/repository/usecase` directory layout but never states the `com.portfolioiq` root package explicitly, so this one correction is not part of what the two new rules were meant to fix):

```
$ javac -d out $(find src -name '*.java')
src/com/portfolioiq/domain/usecase/SellStockUseCase.java:95: error: cannot find symbol
                            existingHolding.getPurchaseDate()
                                           ^
  symbol:   method getPurchaseDate()
  location: variable existingHolding of type StockHolding
1 error
```

Down from 4 errors to 1 — and the 1 remaining error is not either of the two the new rules targeted. Both targeted errors are gone: no arity mismatch on `getHoldings`/`updateHolding`/`deleteHolding`, no call to a nonexistent setter. The one error that remains is new: the agent guessed a `StockHolding(ticker, quantity, purchasePrice, purchaseDate)` constructor with a `getPurchaseDate()` getter, neither of which exists — the real constructor is `StockHolding(String id, String ticker, double quantity, double purchasePrice)`, with no purchase-date field at all. `CLAUDE.md` still doesn't state the model's actual constructor signature or getter list anywhere, only that it's immutable, so this was unreachable from the file as it stands today.

Honest reading of this result: the two added rules worked, precisely and only on what they targeted. This is real, re-run evidence, not an assumption that adding a sentence to a context file would obviously help — it's reported here exactly as it compiled, remaining error included, rather than quietly fixing the new error before reporting a clean pass. The natural further step this surfaces — not yet done, logged here rather than acted on immediately — is to also document `StockHolding`'s real constructor and getters in `CLAUDE.md` and re-run a third time to see whether that closes the last gap.
