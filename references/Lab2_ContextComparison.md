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
