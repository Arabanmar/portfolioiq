package com.portfolioiq.domain.usecase;

import com.portfolioiq.domain.model.StockHolding;
import com.portfolioiq.domain.repository.PortfolioRepository;
import com.portfolioiq.domain.repository.ResultCallback;

/**
 * Story 003 (Add). Takes the raw strings straight off the Add Holding
 * form (same convention as RegisterUseCase: parsing/validation belongs
 * here, not in the Activity), validates them, then delegates to
 * PortfolioRepository. The ticker is normalized to trimmed-uppercase so
 * "aapl" and "AAPL" are always stored/looked-up the same way.
 */
public class AddStockHoldingUseCase {

    private final PortfolioRepository portfolioRepository;

    public AddStockHoldingUseCase(PortfolioRepository portfolioRepository) {
        this.portfolioRepository = portfolioRepository;
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

        String normalizedTicker = ticker.trim().toUpperCase();
        StockHolding holding = new StockHolding(null, normalizedTicker, quantity, purchasePrice);
        portfolioRepository.addHolding(userId, holding, callback);
    }
}
