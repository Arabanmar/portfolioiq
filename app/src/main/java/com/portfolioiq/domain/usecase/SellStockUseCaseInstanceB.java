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
public class SellStockUseCaseInstanceB {

    private static final double QUANTITY_EPSILON = 1e-6;

    private final PortfolioRepository portfolioRepository;

    public SellStockUseCaseInstanceB(PortfolioRepository portfolioRepository) {
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
