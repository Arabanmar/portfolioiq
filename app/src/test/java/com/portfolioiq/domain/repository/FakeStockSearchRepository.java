package com.portfolioiq.domain.repository;

import com.portfolioiq.domain.model.StockSymbol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hand-written test double for StockSearchRepository. knownSymbols is the
 * fake "market": lookupSymbol() finds an exact symbol in it or answers
 * null (unknown stock). searchResultsToReturn is what searchSymbols()
 * returns. errorToReturn makes either call fail, like a network error.
 */
public class FakeStockSearchRepository implements StockSearchRepository {

    public final Map<String, StockSymbol> knownSymbols = new HashMap<>();
    public List<StockSymbol> searchResultsToReturn = new ArrayList<>();
    public String errorToReturn = null;

    public boolean searchCalled = false;
    public String lastQuery;
    public boolean lookupCalled = false;
    public String lastLookup;

    public FakeStockSearchRepository() {
        knownSymbols.put("AAPL", new StockSymbol("AAPL", "APPLE INC"));
        knownSymbols.put("TSLA", new StockSymbol("TSLA", "TESLA INC"));
        knownSymbols.put("MSFT", new StockSymbol("MSFT", "MICROSOFT CORP"));
    }

    @Override
    public void searchSymbols(String query, ResultCallback<List<StockSymbol>> callback) {
        searchCalled = true;
        lastQuery = query;
        if (errorToReturn != null) {
            callback.onError(errorToReturn);
        } else {
            callback.onSuccess(searchResultsToReturn);
        }
    }

    @Override
    public void lookupSymbol(String symbol, ResultCallback<StockSymbol> callback) {
        lookupCalled = true;
        lastLookup = symbol;
        if (errorToReturn != null) {
            callback.onError(errorToReturn);
        } else {
            callback.onSuccess(knownSymbols.get(symbol));
        }
    }
}
