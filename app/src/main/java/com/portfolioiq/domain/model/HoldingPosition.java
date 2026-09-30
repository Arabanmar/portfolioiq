package com.portfolioiq.domain.model;

/**
 * A StockHolding combined with a live price and the numbers computed from
 * it. Per the project's architecture rule, all of these numbers are
 * computed by the app, never by the LLM — this class exists so that rule
 * has one obvious place to live for Story 004.
 *
 * currentPrice falls back to the holding's own purchasePrice if a live
 * quote could not be fetched for its ticker (see GetPortfolioUseCase) —
 * priceIsLive records which case happened, so the UI/tests can tell.
 */
public class HoldingPosition {

    private final StockHolding holding;
    private final double currentPrice;
    private final boolean priceIsLive;
    private final double positionValue;
    private final double profitLoss;
    private final double profitLossPercent;
    private final double allocationPercent;

    public HoldingPosition(StockHolding holding, double currentPrice, boolean priceIsLive,
                            double positionValue, double profitLoss, double profitLossPercent,
                            double allocationPercent) {
        this.holding = holding;
        this.currentPrice = currentPrice;
        this.priceIsLive = priceIsLive;
        this.positionValue = positionValue;
        this.profitLoss = profitLoss;
        this.profitLossPercent = profitLossPercent;
        this.allocationPercent = allocationPercent;
    }

    public StockHolding getHolding() {
        return holding;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public boolean isPriceLive() {
        return priceIsLive;
    }

    public double getPositionValue() {
        return positionValue;
    }

    public double getProfitLoss() {
        return profitLoss;
    }

    public double getProfitLossPercent() {
        return profitLossPercent;
    }

    public double getAllocationPercent() {
        return allocationPercent;
    }
}
