package com.portfolioiq.domain.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.portfolioiq.domain.repository.FakePortfolioRepository;
import com.portfolioiq.domain.repository.ResultCallback;

import org.junit.Before;
import org.junit.Test;

/** Story 003 (Edit). Hand-traced the same way as AddStockHoldingUseCaseTest. */
public class EditStockHoldingUseCaseTest {

    private FakePortfolioRepository fakeRepository;
    private EditStockHoldingUseCase useCase;

    @Before
    public void setUp() {
        fakeRepository = new FakePortfolioRepository();
        useCase = new EditStockHoldingUseCase(fakeRepository);
    }

    @Test
    public void blankHoldingId_showsMissingHoldingError() {
        Result result = execute("  ", "AAPL", "10", "150.00");

        assertEquals("Missing holding to update.", result.error);
        assertFalse(fakeRepository.updateHoldingCalled);
    }

    @Test
    public void blankTicker_showsEnterTickerError() {
        Result result = execute("holding-1", " ", "10", "150.00");

        assertEquals("Enter a stock ticker.", result.error);
        assertFalse(fakeRepository.updateHoldingCalled);
    }

    @Test
    public void nonNumericPurchasePrice_showsValidPriceError() {
        Result result = execute("holding-1", "AAPL", "10", "n/a");

        assertEquals("Enter a valid purchase price.", result.error);
        assertFalse(fakeRepository.updateHoldingCalled);
    }

    @Test
    public void validInput_normalizesTickerAndDelegatesWithSameId() {
        Result result = execute("holding-1", "tsla", "5", "220.50");

        assertTrue(fakeRepository.updateHoldingCalled);
        assertEquals("user-1", fakeRepository.lastUserId);
        assertEquals("holding-1", fakeRepository.lastHolding.getId());
        assertEquals("TSLA", fakeRepository.lastHolding.getTicker());
        assertEquals(5.0, fakeRepository.lastHolding.getQuantity(), 0.0001);
        assertEquals(220.50, fakeRepository.lastHolding.getPurchasePrice(), 0.0001);
        assertTrue(result.success);
    }

    @Test
    public void repositoryError_propagatesToCallback() {
        fakeRepository.errorToReturn = "Could not reach your portfolio. Please try again.";

        Result result = execute("holding-1", "AAPL", "10", "150.00");

        assertEquals("Could not reach your portfolio. Please try again.", result.error);
    }

    private Result execute(String holdingId, String ticker, String quantity, String purchasePrice) {
        Result result = new Result();
        useCase.execute("user-1", holdingId, ticker, quantity, purchasePrice, new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                result.success = true;
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
    }
}
