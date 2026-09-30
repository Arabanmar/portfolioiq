package com.portfolioiq.domain.repository;

import com.portfolioiq.domain.model.StockHolding;

import java.util.ArrayList;
import java.util.List;

/**
 * Hand-written test double for PortfolioRepository (same rationale as
 * FakeAuthRepository: Firestore can't be instantiated on a plain JVM unit
 * test). Records what was called and with what, and lets a test dictate
 * the outcome via errorToReturn / holdingsToReturn / savedHoldingId.
 */
public class FakePortfolioRepository implements PortfolioRepository {

    public boolean getHoldingsCalled = false;
    public boolean addHoldingCalled = false;
    public boolean updateHoldingCalled = false;
    public boolean deleteHoldingCalled = false;

    public String lastUserId;
    public StockHolding lastHolding;
    public String lastHoldingId;

    public List<StockHolding> holdingsToReturn = new ArrayList<>();
    public String savedHoldingId = "fake-holding-id";
    public String errorToReturn = null;

    @Override
    public void getHoldings(String userId, ResultCallback<List<StockHolding>> callback) {
        getHoldingsCalled = true;
        lastUserId = userId;
        if (errorToReturn != null) {
            callback.onError(errorToReturn);
        } else {
            callback.onSuccess(holdingsToReturn);
        }
    }

    @Override
    public void addHolding(String userId, StockHolding holding, ResultCallback<StockHolding> callback) {
        addHoldingCalled = true;
        lastUserId = userId;
        lastHolding = holding;
        if (errorToReturn != null) {
            callback.onError(errorToReturn);
        } else {
            callback.onSuccess(holding.withId(savedHoldingId));
        }
    }

    @Override
    public void updateHolding(String userId, StockHolding holding, ResultCallback<Void> callback) {
        updateHoldingCalled = true;
        lastUserId = userId;
        lastHolding = holding;
        if (errorToReturn != null) {
            callback.onError(errorToReturn);
        } else {
            callback.onSuccess(null);
        }
    }

    @Override
    public void deleteHolding(String userId, String holdingId, ResultCallback<Void> callback) {
        deleteHoldingCalled = true;
        lastUserId = userId;
        lastHoldingId = holdingId;
        if (errorToReturn != null) {
            callback.onError(errorToReturn);
        } else {
            callback.onSuccess(null);
        }
    }
}
