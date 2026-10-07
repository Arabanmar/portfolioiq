package com.portfolioiq.domain.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.portfolioiq.domain.model.StockSymbol;
import com.portfolioiq.domain.repository.FakeStockSearchRepository;
import com.portfolioiq.domain.repository.ResultCallback;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Sprint 3 (doctor's feedback): stock suggestions while typing. */
public class SearchStocksUseCaseTest {

    private FakeStockSearchRepository fakeRepository;
    private SearchStocksUseCase useCase;

    @Before
    public void setUp() {
        fakeRepository = new FakeStockSearchRepository();
        useCase = new SearchStocksUseCase(fakeRepository);
    }

    @Test
    public void blankQuery_returnsNoSuggestionsWithoutSearching() {
        Result result = execute("   ");

        assertTrue(result.suggestions.isEmpty());
        assertFalse("no network call for an empty field", fakeRepository.searchCalled);
    }

    @Test
    public void query_isTrimmedBeforeSearching() {
        execute("  apple ");

        assertTrue(fakeRepository.searchCalled);
        assertEquals("apple", fakeRepository.lastQuery);
    }

    @Test
    public void results_arePassedThroughInOrder() {
        fakeRepository.searchResultsToReturn.add(new StockSymbol("AAPL", "APPLE INC"));
        fakeRepository.searchResultsToReturn.add(new StockSymbol("APLE", "APPLE HOSPITALITY REIT INC"));

        Result result = execute("apple");

        assertEquals(2, result.suggestions.size());
        assertEquals("AAPL", result.suggestions.get(0).getSymbol());
        assertEquals("AAPL - APPLE INC", result.suggestions.get(0).getDisplayText());
        assertNull(result.error);
    }

    @Test
    public void manyResults_areCappedForTheDropdown() {
        List<StockSymbol> many = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            many.add(new StockSymbol("SYM" + i, "COMPANY " + i));
        }
        fakeRepository.searchResultsToReturn = many;

        Result result = execute("s");

        assertEquals(SearchStocksUseCase.MAX_SUGGESTIONS, result.suggestions.size());
        assertEquals("SYM0", result.suggestions.get(0).getSymbol());
    }

    @Test
    public void repositoryError_propagatesToCallback() {
        fakeRepository.errorToReturn = "Could not load stock suggestions.";

        Result result = execute("apple");

        assertEquals("Could not load stock suggestions.", result.error);
    }

    private Result execute(String query) {
        Result result = new Result();
        useCase.execute(query, new ResultCallback<List<StockSymbol>>() {
            @Override
            public void onSuccess(List<StockSymbol> suggestions) {
                result.suggestions = suggestions;
            }

            @Override
            public void onError(String message) {
                result.error = message;
            }
        });
        return result;
    }

    private static class Result {
        List<StockSymbol> suggestions = new ArrayList<>();
        String error;
    }
}
