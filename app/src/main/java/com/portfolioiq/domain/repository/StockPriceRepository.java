package com.portfolioiq.domain.repository;

import java.util.List;
import java.util.Map;

/**
 * Domain-facing contract for live stock prices. The implementation
 * (Finnhub, or anything else later) lives in the data/ package.
 */
public interface StockPriceRepository {

    /**
     * Fetches current prices for the given tickers in one call.
     * Implementations should try every ticker independently: a ticker
     * whose lookup fails may simply be left out of the returned map
     * rather than failing the whole batch, so GetPortfolioUseCase can
     * still show the tickers that did succeed (see its Javadoc for how a
     * missing entry is handled).
     */
    void getQuotes(List<String> tickers, ResultCallback<Map<String, Double>> callback);
}
