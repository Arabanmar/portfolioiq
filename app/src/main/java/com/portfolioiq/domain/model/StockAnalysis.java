package com.portfolioiq.domain.model;

/**
 * TradingAgents' verdict on one stock: a five-level rating (Buy, Overweight,
 * Hold, Underweight, Sell) and the portfolio manager agent's decision text.
 */
public class StockAnalysis {

    private final String ticker;
    private final String rating;
    private final String decision;

    public StockAnalysis(String ticker, String rating, String decision) {
        this.ticker = ticker;
        this.rating = rating;
        this.decision = decision;
    }

    public String getTicker() {
        return ticker;
    }

    public String getRating() {
        return rating;
    }

    public String getDecision() {
        return decision;
    }
}
