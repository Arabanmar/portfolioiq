package com.portfolioiq.domain.repository;

import com.portfolioiq.domain.model.StockSymbol;

import java.util.List;

/**
 * Domain-facing contract for finding real stock symbols (doctor's feedback,
 * Sprint 3: suggest stocks while the user types, and never save a ticker
 * that doesn't exist). The implementation (Finnhub) lives in data/.
 */
public interface StockSearchRepository {

    /** Stocks whose symbol or company name matches the query, best matches first. */
    void searchSymbols(String query, ResultCallback<List<StockSymbol>> callback);

    /**
     * Looks up one exact symbol. onSuccess(null) means the symbol does not
     * exist; onError means the check itself failed (e.g. no network).
     */
    void lookupSymbol(String symbol, ResultCallback<StockSymbol> callback);
}
