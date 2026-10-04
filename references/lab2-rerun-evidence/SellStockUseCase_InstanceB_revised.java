package com.portfolioiq.domain.usecase;

import java.util.List;

import com.portfolioiq.domain.model.StockHolding;
import com.portfolioiq.domain.repository.PortfolioRepository;
import com.portfolioiq.domain.repository.ResultCallback;

/**
 * Use case for selling part or all of an existing stock holding.
 *
 * Follows the same shape as AddStockHoldingUseCase: raw UI strings in,
 * validation first (bailing out via callback.onError on the first failure),
 * normalization at the validation boundary, and the repository called only
 * once every check has passed. Results come back via ResultCallback<Void>,
 * matching PortfolioRepository's other write methods.
 */
public class SellStockUseCase {

    private final PortfolioRepository portfolioRepository;

    public SellStockUseCase(PortfolioRepository portfolioRepository) {
        this.portfolioRepository = portfolioRepository;
    }

    public void execute(String userId,
                         String holdingId,
                         String quantityToSellText,
                         ResultCallback<Void> callback) {

        if (userId == null || userId.trim().isEmpty()) {
            callback.onError("You must be signed in to sell a stock.");
            return;
        }

        if (holdingId == null || holdingId.trim().isEmpty()) {
            callback.onError("No holding was selected to sell.");
            return;
        }

        if (quantityToSellText == null || quantityToSellText.trim().isEmpty()) {
            callback.onError("Please enter a quantity to sell.");
            return;
        }

        final String normalizedUserId = userId.trim();
        final String normalizedHoldingId = holdingId.trim();
        final String normalizedQuantityText = quantityToSellText.trim();

        final double quantityToSell;
        try {
            quantityToSell = Double.parseDouble(normalizedQuantityText);
        } catch (NumberFormatException e) {
            callback.onError("Quantity must be a valid number.");
            return;
        }

        if (quantityToSell <= 0) {
            callback.onError("Quantity to sell must be greater than zero.");
            return;
        }

        portfolioRepository.getHoldings(normalizedUserId, new ResultCallback<List<StockHolding>>() {
            @Override
            public void onSuccess(List<StockHolding> holdings) {
                StockHolding existingHolding = null;
                if (holdings != null) {
                    for (StockHolding holding : holdings) {
                        if (holding.getId() != null && holding.getId().equals(normalizedHoldingId)) {
                            existingHolding = holding;
                            break;
                        }
                    }
                }

                if (existingHolding == null) {
                    callback.onError("That holding could not be found.");
                    return;
                }

                if (quantityToSell > existingHolding.getQuantity()) {
                    callback.onError("You can't sell more shares than you currently hold.");
                    return;
                }

                double remainingQuantity = existingHolding.getQuantity() - quantityToSell;

                if (remainingQuantity == 0) {
                    portfolioRepository.deleteHolding(normalizedUserId, normalizedHoldingId, callback);
                } else {
                    StockHolding updatedHolding = new StockHolding(
                            existingHolding.getTicker(),
                            remainingQuantity,
                            existingHolding.getPurchasePrice(),
                            existingHolding.getPurchaseDate()
                    ).withId(normalizedHoldingId);

                    portfolioRepository.updateHolding(normalizedUserId, updatedHolding, callback);
                }
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }
}
