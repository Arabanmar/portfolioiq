package com.portfolioiq.domain.usecase;

import com.portfolioiq.domain.repository.PortfolioRepository;
import com.portfolioiq.domain.repository.ResultCallback;

/** Story 003 (Delete) — removing a holding from the dashboard. */
public class DeleteStockHoldingUseCase {

    private final PortfolioRepository portfolioRepository;

    public DeleteStockHoldingUseCase(PortfolioRepository portfolioRepository) {
        this.portfolioRepository = portfolioRepository;
    }

    public void execute(String userId, String holdingId, ResultCallback<Void> callback) {
        if (holdingId == null || holdingId.trim().isEmpty()) {
            callback.onError("Missing holding to delete.");
            return;
        }
        portfolioRepository.deleteHolding(userId, holdingId, callback);
    }
}
