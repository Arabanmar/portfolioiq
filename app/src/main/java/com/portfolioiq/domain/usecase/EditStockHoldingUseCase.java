package com.portfolioiq.domain.usecase;

import com.portfolioiq.domain.model.StockHolding;
import com.portfolioiq.domain.repository.PortfolioRepository;
import com.portfolioiq.domain.repository.ResultCallback;

/**
 * Story 003 (Edit). Same validation as AddStockHoldingUseCase, plus a
 * check that a holdingId was actually supplied (Edit always operates on
 * an existing document, unlike Add).
 */
public class EditStockHoldingUseCase {

    private final PortfolioRepository portfolioRepository;

    public EditStockHoldingUseCase(PortfolioRepository portfolioRepository) {
        this.portfolioRepository = portfolioRepository;
    }

    public void execute(String userId, String holdingId, String ticker, String quantityText,
                         String purchasePriceText, ResultCallback<Void> callback) {
        if (holdingId == null || holdingId.trim().isEmpty()) {
            callback.onError("Missing holding to update.");
            return;
        }
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
        StockHolding holding = new StockHolding(holdingId, normalizedTicker, quantity, purchasePrice);
        portfolioRepository.updateHolding(userId, holding, callback);
    }
}
