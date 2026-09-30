package com.portfolioiq.domain.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hand-written test double for StockPriceRepository. pricesToReturn lets a
 * test set exactly which tickers "have a live quote" -- a ticker requested
 * but absent from this map is exactly the "quote lookup failed for this
 * one" case GetPortfolioUseCase's Javadoc describes.
 */
public class FakeStockPriceRepository implements StockPriceRepository {

    public boolean getQuotesCalled = false;
    public List<String> lastTickers;

    public Map<String, Double> pricesToReturn = new HashMap<>();
    public String errorToReturn = null;

    @Override
    public void getQuotes(List<String> tickers, ResultCallback<Map<String, Double>> callback) {
        getQuotesCalled = true;
        lastTickers = tickers;
        if (errorToReturn != null) {
            callback.onError(errorToReturn);
        } else {
            callback.onSuccess(pricesToReturn);
        }
    }
}
