package com.portfolioiq.data.holding;

import java.math.BigDecimal;

public class SellStockUseCaseInstanceA {

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

    public SellStockUseCaseInstanceA(StockHoldingRepository repository) {
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
