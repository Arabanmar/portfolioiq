package com.portfolioiq.domain.usecase;

import com.portfolioiq.domain.model.HoldingPosition;
import com.portfolioiq.domain.model.PortfolioSummary;
import com.portfolioiq.domain.model.StockHolding;
import com.portfolioiq.domain.repository.PortfolioRepository;
import com.portfolioiq.domain.repository.ResultCallback;
import com.portfolioiq.domain.repository.StockPriceRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Story 004 — Portfolio Dashboard & Calculations.
 *
 * This is the one place all the dashboard's numbers get computed, per the
 * project's architecture rule (app calculates everything; the LLM, in a
 * later story, only ever interprets an already-built PortfolioSummary like
 * this one). Two async steps, chained: load the user's holdings, then look
 * up a live price for every distinct ticker among them.
 *
 * Known simplification, documented rather than hidden: if
 * StockPriceRepository.getQuotes() comes back without an entry for some
 * ticker (a bad symbol, a rate limit, a network blip), that holding is
 * still shown, with currentPrice falling back to its own purchasePrice —
 * i.e. it displays as breaking even (profitLoss = 0) rather than
 * disappearing from the dashboard. HoldingPosition.isPriceLive() is false
 * in that case, so the UI can flag it if it wants to.
 */
public class GetPortfolioUseCase {

    private final PortfolioRepository portfolioRepository;
    private final StockPriceRepository stockPriceRepository;

    public GetPortfolioUseCase(PortfolioRepository portfolioRepository,
                                StockPriceRepository stockPriceRepository) {
        this.portfolioRepository = portfolioRepository;
        this.stockPriceRepository = stockPriceRepository;
    }

    public void execute(String userId, ResultCallback<PortfolioSummary> callback) {
        portfolioRepository.getHoldings(userId, new ResultCallback<List<StockHolding>>() {
            @Override
            public void onSuccess(List<StockHolding> holdings) {
                if (holdings.isEmpty()) {
                    callback.onSuccess(PortfolioSummary.empty());
                    return;
                }

                Set<String> tickers = new HashSet<>();
                for (StockHolding holding : holdings) {
                    tickers.add(holding.getTicker());
                }

                stockPriceRepository.getQuotes(new ArrayList<>(tickers),
                        new ResultCallback<Map<String, Double>>() {
                            @Override
                            public void onSuccess(Map<String, Double> prices) {
                                callback.onSuccess(buildSummary(holdings, prices));
                            }

                            @Override
                            public void onError(String message) {
                                callback.onError(message);
                            }
                        });
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private PortfolioSummary buildSummary(List<StockHolding> holdings, Map<String, Double> prices) {
        List<HoldingPosition> positions = new ArrayList<>();
        double totalValue = 0;
        double totalCost = 0;

        // Pass 1: per-holding numbers that don't depend on the portfolio
        // total (that total isn't known until every holding is priced).
        for (StockHolding holding : holdings) {
            Double livePrice = prices.get(holding.getTicker());
            boolean priceIsLive = livePrice != null;
            double currentPrice = priceIsLive ? livePrice : holding.getPurchasePrice();

            double positionValue = holding.getQuantity() * currentPrice;
            double costBasis = holding.getQuantity() * holding.getPurchasePrice();
            double profitLoss = positionValue - costBasis;
            double profitLossPercent = costBasis > 0 ? (profitLoss / costBasis) * 100 : 0;

            totalValue += positionValue;
            totalCost += costBasis;

            // allocationPercent needs totalValue, filled in during pass 2.
            positions.add(new HoldingPosition(holding, currentPrice, priceIsLive, positionValue,
                    profitLoss, profitLossPercent, 0));
        }

        double totalProfitLoss = totalValue - totalCost;
        double totalProfitLossPercent = totalCost > 0 ? (totalProfitLoss / totalCost) * 100 : 0;

        // Pass 2: now that totalValue is known, rebuild each position with
        // its real allocationPercent. HoldingPosition is immutable, so this
        // is a rebuild, not a mutation.
        List<HoldingPosition> finalPositions = new ArrayList<>();
        for (HoldingPosition position : positions) {
            double allocationPercent = totalValue > 0
                    ? (position.getPositionValue() / totalValue) * 100
                    : 0;
            finalPositions.add(new HoldingPosition(position.getHolding(), position.getCurrentPrice(),
                    position.isPriceLive(), position.getPositionValue(), position.getProfitLoss(),
                    position.getProfitLossPercent(), allocationPercent));
        }

        return new PortfolioSummary(finalPositions, totalValue, totalCost, totalProfitLoss,
                totalProfitLossPercent);
    }
}
