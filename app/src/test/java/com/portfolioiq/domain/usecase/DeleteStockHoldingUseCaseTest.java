package com.portfolioiq.domain.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.portfolioiq.domain.repository.FakePortfolioRepository;
import com.portfolioiq.domain.repository.ResultCallback;

import org.junit.Before;
import org.junit.Test;

/** Story 003 (Delete). Hand-traced the same way as the other Sprint 2 use-case tests. */
public class DeleteStockHoldingUseCaseTest {

    private FakePortfolioRepository fakeRepository;
    private DeleteStockHoldingUseCase useCase;

    @Before
    public void setUp() {
        fakeRepository = new FakePortfolioRepository();
        useCase = new DeleteStockHoldingUseCase(fakeRepository);
    }

    @Test
    public void blankHoldingId_showsMissingHoldingError() {
        Result result = execute("   ");

        assertEquals("Missing holding to delete.", result.error);
        assertFalse(fakeRepository.deleteHoldingCalled);
    }

    @Test
    public void validHoldingId_delegatesToRepository() {
        Result result = execute("holding-1");

        assertTrue(fakeRepository.deleteHoldingCalled);
        assertEquals("user-1", fakeRepository.lastUserId);
        assertEquals("holding-1", fakeRepository.lastHoldingId);
        assertTrue(result.success);
    }

    @Test
    public void repositoryError_propagatesToCallback() {
        fakeRepository.errorToReturn = "Could not reach your portfolio. Please try again.";

        Result result = execute("holding-1");

        assertEquals("Could not reach your portfolio. Please try again.", result.error);
    }

    private Result execute(String holdingId) {
        Result result = new Result();
        useCase.execute("user-1", holdingId, new ResultCallback<Void>() {
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
