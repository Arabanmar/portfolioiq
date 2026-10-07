package com.portfolioiq.domain.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.portfolioiq.domain.model.StockHolding;
import com.portfolioiq.domain.repository.FakePortfolioRepository;
import com.portfolioiq.domain.repository.FakeStockSearchRepository;
import com.portfolioiq.domain.repository.ResultCallback;

import org.junit.Before;
import org.junit.Test;

/**
 * Story 003 (Add). Originally hand-traced (Gradle couldn't run on the
 * team laptop at the time). Since the Sprint 3 changes, the whole domain
 * test suite has also been run for real with JUnit 4.13.2 (javac + JUnitCore
 * against the domain layer, which has no Android imports).
 */
public class AddStockHoldingUseCaseTest {

    private FakePortfolioRepository fakeRepository;
    private FakeStockSearchRepository fakeStockSearch;
    private AddStockHoldingUseCase useCase;

    @Before
    public void setUp() {
        fakeRepository = new FakePortfolioRepository();
        fakeStockSearch = new FakeStockSearchRepository(); // knows AAPL, TSLA, MSFT
        useCase = new AddStockHoldingUseCase(fakeRepository, fakeStockSearch);
    }

    @Test
    public void blankTicker_showsEnterTickerError() {
        Result result = execute("  ", "10", "150.00");

        assertEquals("Enter a stock ticker.", result.error);
        assertFalse(fakeRepository.addHoldingCalled);
    }

    @Test
    public void nonNumericQuantity_showsValidQuantityError() {
        Result result = execute("AAPL", "abc", "150.00");

        assertEquals("Enter a valid quantity.", result.error);
        assertFalse(fakeRepository.addHoldingCalled);
    }

    @Test
    public void zeroQuantity_showsQuantityGreaterThanZeroError() {
        Result result = execute("AAPL", "0", "150.00");

        assertEquals("Quantity must be greater than zero.", result.error);
        assertFalse(fakeRepository.addHoldingCalled);
    }

    @Test
    public void negativePurchasePrice_showsPriceGreaterThanZeroError() {
        Result result = execute("AAPL", "10", "-5");

        assertEquals("Purchase price must be greater than zero.", result.error);
        assertFalse(fakeRepository.addHoldingCalled);
    }

    @Test
    public void validInput_normalizesTickerAndDelegates() {
        Result result = execute("aapl", "10", "150.00");

        assertTrue(fakeRepository.addHoldingCalled);
        assertEquals("user-1", fakeRepository.lastUserId);
        assertEquals("AAPL", fakeRepository.lastHolding.getTicker());
        assertEquals(10.0, fakeRepository.lastHolding.getQuantity(), 0.0001);
        assertEquals(150.00, fakeRepository.lastHolding.getPurchasePrice(), 0.0001);
        assertTrue(result.success);
        assertEquals("fake-holding-id", result.savedHolding.getId());
    }

    @Test
    public void repositoryError_propagatesToCallback() {
        fakeRepository.errorToReturn = "Could not reach your portfolio. Please try again.";

        Result result = execute("AAPL", "10", "150.00");

        assertEquals("Could not reach your portfolio. Please try again.", result.error);
    }

    // --- Real-stock check (Sprint 3, doctor's feedback) ---

    @Test
    public void unknownTicker_isRejectedAndNothingSaved() {
        Result result = execute("ZZZZQ", "10", "150.00");

        assertTrue(fakeStockSearch.lookupCalled);
        assertEquals("ZZZZQ", fakeStockSearch.lastLookup);
        assertEquals("Unknown stock symbol. Pick a stock from the suggestions.", result.error);
        assertFalse(fakeRepository.addHoldingCalled);
    }

    @Test
    public void invalidQuantity_isRejectedBeforeAnyStockLookup() {
        Result result = execute("AAPL", "0", "150.00");

        assertEquals("Quantity must be greater than zero.", result.error);
        assertFalse("local checks run first, no network call", fakeStockSearch.lookupCalled);
    }

    @Test
    public void stockLookupFailure_isReportedAndNothingSaved() {
        fakeStockSearch.errorToReturn = "Could not check the stock symbol. Please try again.";

        Result result = execute("AAPL", "10", "150.00");

        assertEquals("Could not check the stock symbol. Please try again.", result.error);
        assertFalse(fakeRepository.addHoldingCalled);
    }

    private Result execute(String ticker, String quantity, String purchasePrice) {
        Result result = new Result();
        useCase.execute("user-1", ticker, quantity, purchasePrice, new ResultCallback<StockHolding>() {
            @Override
            public void onSuccess(StockHolding holding) {
                result.success = true;
                result.savedHolding = holding;
            }

            @Override
            public void onError(String message) {
                result.error = message;
            }
        });
        return result;
    }

    private static class Result {
        boolean success;
        String error;
        StockHolding savedHolding;
    }
}
