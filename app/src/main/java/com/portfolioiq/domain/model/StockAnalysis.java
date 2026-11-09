package com.portfolioiq.domain.model;

/**
 * TradingAgents' verdict on one stock: a five-level rating (Buy, Overweight,
 * Hold, Underweight, Sell), the portfolio manager agent's decision text, how
 * much the agents agreed with each other, and what they read. The last two are
 * null for an older AI server that does not send them.
 */
public class StockAnalysis {

    private final String ticker;
    private final String rating;
    private final String decision;
    private final AgentConfidence confidence;
    private final AgentReading reading;

    public StockAnalysis(String ticker, String rating, String decision,
                         AgentConfidence confidence, AgentReading reading) {
        this.ticker = ticker;
        this.rating = rating;
        this.decision = decision;
        this.confidence = confidence;
        this.reading = reading;
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

    public AgentConfidence getConfidence() {
        return confidence;
    }

    public AgentReading getReading() {
        return reading;
    }
}
