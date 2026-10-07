package com.portfolioiq.domain.usecase;

import com.portfolioiq.domain.model.StockHolding;
import com.portfolioiq.domain.model.StockSymbol;
import com.portfolioiq.domain.repository.PortfolioRepository;
import com.portfolioiq.domain.repository.ResultCallback;
import com.portfolioiq.domain.repository.StockSearchRepository;

/**
 * Story 003 (Add). Takes the raw strings straight off the Add Holding
 * form (same convention as RegisterUseCase: parsing/validation belongs
 * here, not in the Activity), validates them, then delegates to
 * PortfolioRepository. The ticker is normalized to trimmed-uppercase so
 * "aapl" and "AAPL" are always stored/looked-up the same way, and since
 * Sprint 3 it must be a real stock symbol (checked via StockSearchRepository).
 */
public class AddStockHoldingUseCase {

    static final String UNKNOWN_STOCK_ERROR = "Unknown stock symbol. Pick a stock from the suggestions.";

    private final PortfolioRepository portfolioRepository;
    private final StockSearchRepository stockSearchRepository;

    public AddStockHoldingUseCase(PortfolioRepository portfolioRepository,
                         StockSearchRepository stockSearchRepository) {
        this.portfolioRepository = portfolioRepository;
        this.stockSearchRepository = stockSearchRepository;
    }

    public void execute(String userId, String ticker, String quantityText, String purchasePriceText,
                         ResultCallback<StockHolding> callback) {
        if (ticker == null || ticker.trim().isEmpty()) {
            callback.onError("Enter a stock ticker.");
            return;
        }

        double quantity;
        try {
            quantity = Double.parseDouble(quantityText);
        } catch (NumberFormatException | NullPointerException e) {
            callback.onError("Enter a valid quantity.");
            return;
        }
        if (quantity <= 0) {
            callback.onError("Quantity must be greater than zero.");
            return;
        }

        double purchasePrice;
        try {
            purchasePrice = Double.parseDouble(purchasePriceText);
        } catch (NumberFormatException | NullPointerException e) {
            callback.onError("Enter a valid purchase price.");
            return;
        }
        if (purchasePrice <= 0) {
            callback.onError("Purchase price must be greater than zero.");
            return;
        }

        // Sprint 3 (doctor's feedback): only real stocks can be saved. This is
        // the last check, after every local one has passed, and it is the one
        // place the rule lives, so a typed (not picked) wrong ticker is
        // rejected even if the suggestion list was skipped.
        String normalizedTicker = ticker.trim().toUpperCase();
        final double validQuantity = quantity;
        final double validPurchasePrice = purchasePrice;
        stockSearchRepository.lookupSymbol(normalizedTicker, new ResultCallback<StockSymbol>() {
            @Override
            public void onSuccess(StockSymbol match) {
                if (match == null) {
                    callback.onError(UNKNOWN_STOCK_ERROR);
                    return;
                }
                StockHolding holding = new StockHolding(null, normalizedTicker, validQuantity, validPurchasePrice);
                portfolioRepository.addHolding(userId, holding, callback);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }
}
