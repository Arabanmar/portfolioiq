package com.portfolioiq.domain.repository;

import com.portfolioiq.domain.model.StockHolding;

import java.util.List;

/**
 * Domain-facing contract for storing a user's stock holdings. The
 * implementation (Firestore, or anything else later) lives in the data/
 * package, same split as AuthRepository.
 */
public interface PortfolioRepository {

    void getHoldings(String userId, ResultCallback<List<StockHolding>> callback);

    /** callback receives the saved holding, now carrying its generated id. */
    void addHolding(String userId, StockHolding holding, ResultCallback<StockHolding> callback);

    void updateHolding(String userId, StockHolding holding, ResultCallback<Void> callback);

    void deleteHolding(String userId, String holdingId, ResultCallback<Void> callback);
}
