package com.portfolioiq.domain.model;

/**
 * How much the TradingAgents team agreed with itself about a stock (or, for the
 * whole portfolio, the average of its stocks). Calculated by code on the AI
 * server. It measures agreement between the agents, not the chance of making
 * money.
 */
public class AgentConfidence {

    private final double percent;
    private final String level;

    public AgentConfidence(double percent, String level) {
        this.percent = percent;
        this.level = level;
    }

    /** 10 to 95. */
    public double getPercent() { return percent; }

    /** "High", "Medium" or "Low". */
    public String getLevel() { return level; }
}
