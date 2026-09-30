package com.portfolioiq.domain.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.portfolioiq.domain.model.HoldingPosition;
import com.portfolioiq.domain.model.PortfolioSummary;
import com.portfolioiq.domain.model.StockHolding;
import com.portfolioiq.domain.repository.FakePortfolioRepository;
import com.portfolioiq.domain.repository.FakeStockPriceRepository;
import com.portfolioiq.domain.repository.ResultCallback;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Story 004. This is the use case doing the actual dashboard math, so it
 * gets the most thorough hand-trace of the Sprint 2 suite: every expected
 * number below (position value, P/L, P/L%, allocation%, totals) was
 * computed by hand against GetPortfolioUseCase.java's buildSummary()
 * logic and cross-checked line-by-line, the same discipline Sprint 1's
 * RegisterUseCaseTest/LoginUseCaseTest used -- not an actual JUnit run,
 * since Anmar's laptop still can't reliably run Gradle.
 */
public class GetPortfolioUseCaseTest {

    private static final double DELTA = 0.01;

    private FakePortfolioRepository fakePortfolioRepository;
    private FakeStockPriceRepository fakeStockPriceRepository;
    private GetPortfolioUseCase useCase;

    @Before
    public void setUp() {
        fakePortfolioRepository = new FakePortfolioRepository();
        fakeStockPriceRepository = new FakeStockPriceRepository();
        useCase = new GetPortfolioUseCase(fakePortfolioRepository, fakeStockPriceRepository);
    }

    @Test
    public void noHoldings_returnsEmptySummaryWithoutFetchingPrices() {
        fakePortfolioRepository.holdingsToReturn = new ArrayList<>();

        PortfolioSummary summary = execute();

        assertTrue(summary.getPositions().isEmpty());
        assertEquals(0, summary.getTotalValue(), DELTA);
        assertEquals(0, summary.getTotalCost(), DELTA);
        assertEquals(0, summary.getTotalProfitLoss(), DELTA);
        assertEquals(0, summary.getTotalProfitLossPercent(), DELTA);
        assertFalse("getQuotes should be skipped when there are no holdings",
                fakeStockPriceRepository.getQuotesCalled);
    }

    @Test
    public void singleHolding_liveGain_computesPositionAndTotalsCorrectly() {
        // 10 shares bought at $100, now $120: +$200, +20%.
        fakePortfolioRepository.holdingsToReturn = holdings(
                new StockHolding("h1", "AAPL", 10, 100));
        fakeStockPriceRepository.pricesToReturn = prices("AAPL", 120.0);

        PortfolioSummary summary = execute();

        assertEquals(1, summary.getPositions().size());
        HoldingPosition position = summary.getPositions().get(0);
        assertTrue(position.isPriceLive());
        assertEquals(120.0, position.getCurrentPrice(), DELTA);
        assertEquals(1200.0, position.getPositionValue(), DELTA);
        assertEquals(200.0, position.getProfitLoss(), DELTA);
        assertEquals(20.0, position.getProfitLossPercent(), DELTA);
        assertEquals(100.0, position.getAllocationPercent(), DELTA);

        assertEquals(1200.0, summary.getTotalValue(), DELTA);
        assertEquals(1000.0, summary.getTotalCost(), DELTA);
        assertEquals(200.0, summary.getTotalProfitLoss(), DELTA);
        assertEquals(20.0, summary.getTotalProfitLossPercent(), DELTA);
    }

    @Test
    public void missingQuote_fallsBackToPurchasePriceAndFlagsNotLive() {
        // No live price came back for ZZZZ -- currentPrice falls back to
        // purchasePrice, so it shows as breaking even rather than vanishing.
        fakePortfolioRepository.holdingsToReturn = holdings(
                new StockHolding("h1", "ZZZZ", 5, 50));
        fakeStockPriceRepository.pricesToReturn = new java.util.HashMap<>(); // empty: no quote found

        PortfolioSummary summary = execute();

        HoldingPosition position = summary.getPositions().get(0);
        assertFalse(position.isPriceLive());
        assertEquals(50.0, position.getCurrentPrice(), DELTA);
        assertEquals(250.0, position.getPositionValue(), DELTA);
        assertEquals(0.0, position.getProfitLoss(), DELTA);
        assertEquals(0.0, position.getProfitLossPercent(), DELTA);
        assertEquals(100.0, position.getAllocationPercent(), DELTA);
    }

    @Test
    public void twoHoldings_allocationPercentSplitsByPositionValue() {
        // AAPL: 10 @ $100, now $120 -> value $1200, cost $1000, P/L +$200 (+20%)
        // MSFT: 4 @ $200, now $250 -> value $1000, cost $800, P/L +$200 (+25%)
        // total value $2200, total cost $1800, total P/L +$400 (+22.222...%)
        fakePortfolioRepository.holdingsToReturn = holdings(
                new StockHolding("h1", "AAPL", 10, 100),
                new StockHolding("h2", "MSFT", 4, 200));
        fakeStockPriceRepository.pricesToReturn = prices("AAPL", 120.0, "MSFT", 250.0);

        PortfolioSummary summary = execute();

        assertEquals(2, summary.getPositions().size());
        HoldingPosition aapl = findPosition(summary, "AAPL");
        HoldingPosition msft = findPosition(summary, "MSFT");

        assertEquals(1200.0, aapl.getPositionValue(), DELTA);
        assertEquals(1000.0, msft.getPositionValue(), DELTA);
        assertEquals(54.545, aapl.getAllocationPercent(), 0.01);
        assertEquals(45.454, msft.getAllocationPercent(), 0.01);

        assertEquals(2200.0, summary.getTotalValue(), DELTA);
        assertEquals(1800.0, summary.getTotalCost(), DELTA);
        assertEquals(400.0, summary.getTotalProfitLoss(), DELTA);
        assertEquals(22.222, summary.getTotalProfitLossPercent(), 0.01);
    }

    @Test
    public void holdingsFetchError_propagatesWithoutFetchingPrices() {
        fakePortfolioRepository.errorToReturn = "Could not reach your portfolio. Please try again.";

        Result result = executeExpectingError();

        assertEquals("Could not reach your portfolio. Please try again.", result.error);
        assertFalse(fakeStockPriceRepository.getQuotesCalled);
    }

    @Test
    public void priceFetchError_propagatesToCallback() {
        fakePortfolioRepository.holdingsToReturn = holdings(
                new StockHolding("h1", "AAPL", 10, 100));
        fakeStockPriceRepository.errorToReturn = "Could not fetch live prices. Please try again.";

        Result result = executeExpectingError();

        assertEquals("Could not fetch live prices. Please try again.", result.error);
        assertNull(result.summary);
    }

    private HoldingPosition findPosition(PortfolioSummary summary, String ticker) {
        for (HoldingPosition position : summary.getPositions()) {
            if (position.getHolding().getTicker().equals(ticker)) {
                return position;
            }
        }
        throw new AssertionError("No position for " + ticker);
    }

    private List<StockHolding> holdings(StockHolding... items) {
        List<StockHolding> list = new ArrayList<>();
        for (StockHolding item : items) {
            list.add(item);
        }
        return list;
    }

    private java.util.Map<String, Double> prices(Object... tickerPricePairs) {
        java.util.Map<String, Double> map = new java.util.HashMap<>();
        for (int i = 0; i < tickerPricePairs.length; i += 2) {
            map.put((String) tickerPricePairs[i], (Double) tickerPricePairs[i + 1]);
        }
        return map;
    }

    private PortfolioSummary execute() {
        Result result = executeExpectingError();
        if (result.summary == null) {
            throw new AssertionError("Expected success but got error: " + result.error);
        }
        return result.summary;
    }

    private Result executeExpectingError() {
        Result result = new Result();
        useCase.execute("user-1", new ResultCallback<PortfolioSummary>() {
            @Override
            public void onSuccess(PortfolioSummary summary) {
                result.summary = summary;
            }

            @Override
            public void onError(String message) {
                result.error = message;
            }
        });
        return result;
    }

    private static class Result {
        PortfolioSummary summary;
        String error;
    }
}
