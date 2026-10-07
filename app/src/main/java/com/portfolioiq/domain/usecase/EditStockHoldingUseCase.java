package com.portfolioiq.domain.usecase;

import com.portfolioiq.domain.model.StockHolding;
import com.portfolioiq.domain.model.StockSymbol;
import com.portfolioiq.domain.repository.PortfolioRepository;
import com.portfolioiq.domain.repository.ResultCallback;
import com.portfolioiq.domain.repository.StockSearchRepository;

/**
 * Story 003 (Edit). Same validation as AddStockHoldingUseCase (including
 * the Sprint 3 real-stock check), plus a
 * check that a holdingId was actually supplied (Edit always operates on
 * an existing document, unlike Add).
 */
public class EditStockHoldingUseCase {

    static final String UNKNOWN_STOCK_ERROR = "Unknown stock symbol. Pick a stock from the suggestions.";

    private final PortfolioRepository portfolioRepository;
    private final StockSearchRepository stockSearchRepository;

    public EditStockHoldingUseCase(PortfolioRepository portfolioRepository,
                         StockSearchRepository stockSearchRepository) {
        this.portfolioRepository = portfolioRepository;
        this.stockSearchRepository = stockSearchRepository;
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

        // Same real-stock check as AddStockHoldingUseCase (Sprint 3).
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
                StockHolding holding = new StockHolding(holdingId, normalizedTicker, validQuantity, validPurchasePrice);
                portfolioRepository.updateHolding(userId, holding, callback);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }
}
