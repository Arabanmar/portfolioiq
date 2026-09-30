package com.portfolioiq.domain.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.portfolioiq.domain.model.StockHolding;
import com.portfolioiq.domain.repository.FakePortfolioRepository;
import com.portfolioiq.domain.repository.ResultCallback;

import org.junit.Before;
import org.junit.Test;

/**
 * Story 003 (Add). Hand-traced against AddStockHoldingUseCase.java the
 * same way Sprint 1's RegisterUseCaseTest was -- Anmar's laptop still
 * can't reliably run Gradle, so these are verified by reading the source
 * line-by-line against each test's inputs and expected branch, not by an
 * actual JUnit run.
 */
public class AddStockHoldingUseCaseTest {

    private FakePortfolioRepository fakeRepository;
    private AddStockHoldingUseCase useCase;

    @Before
    public void setUp() {
        fakeRepository = new FakePortfolioRepository();
        useCase = new AddStockHoldingUseCase(fakeRepository);
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
