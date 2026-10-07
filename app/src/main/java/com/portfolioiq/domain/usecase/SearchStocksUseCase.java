package com.portfolioiq.domain.usecase;

import com.portfolioiq.domain.model.StockSymbol;
import com.portfolioiq.domain.repository.ResultCallback;
import com.portfolioiq.domain.repository.StockSearchRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Doctor's feedback (Sprint 3): as the user types the first letters of a
 * stock, show matching stocks to pick from. Takes the raw text off the
 * ticker field, like every other use case here.
 */
public class SearchStocksUseCase {

    /** Enough to choose from without making the dropdown longer than the screen. */
    static final int MAX_SUGGESTIONS = 8;

    private final StockSearchRepository stockSearchRepository;

    public SearchStocksUseCase(StockSearchRepository stockSearchRepository) {
        this.stockSearchRepository = stockSearchRepository;
    }

    public void execute(String queryText, ResultCallback<List<StockSymbol>> callback) {
        if (queryText == null || queryText.trim().isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }
        stockSearchRepository.searchSymbols(queryText.trim(), new ResultCallback<List<StockSymbol>>() {
            @Override
            public void onSuccess(List<StockSymbol> result) {
                List<StockSymbol> suggestions = result == null ? new ArrayList<>() : new ArrayList<>(result);
                if (suggestions.size() > MAX_SUGGESTIONS) {
                    suggestions = new ArrayList<>(suggestions.subList(0, MAX_SUGGESTIONS));
                }
                callback.onSuccess(suggestions);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }
}
