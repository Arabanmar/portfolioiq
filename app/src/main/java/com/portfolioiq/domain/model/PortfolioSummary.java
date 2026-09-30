package com.portfolioiq.domain.model;

import java.util.Collections;
import java.util.List;

/**
 * Everything Story 004's dashboard needs to render: one row per holding
 * (already carrying its own computed numbers) plus the portfolio-wide
 * totals. Built by GetPortfolioUseCase; the LLM (Story 005/006, later)
 * will only ever be handed the already-computed numbers from an object
 * like this, never raw prices, per the project's architecture rule.
 */
public class PortfolioSummary {

    private final List<HoldingPosition> positions;
    private final double totalValue;
    private final double totalCost;
    private final double totalProfitLoss;
    private final double totalProfitLossPercent;

    public PortfolioSummary(List<HoldingPosition> positions, double totalValue, double totalCost,
                             double totalProfitLoss, double totalProfitLossPercent) {
        this.positions = Collections.unmodifiableList(positions);
        this.totalValue = totalValue;
        this.totalCost = totalCost;
        this.totalProfitLoss = totalProfitLoss;
        this.totalProfitLossPercent = totalProfitLossPercent;
    }

    public static PortfolioSummary empty() {
        return new PortfolioSummary(Collections.emptyList(), 0, 0, 0, 0);
    }

    public List<HoldingPosition> getPositions() {
        return positions;
    }

    public double getTotalValue() {
        return totalValue;
    }

    public double getTotalCost() {
        return totalCost;
    }

    public double getTotalProfitLoss() {
        return totalProfitLoss;
    }

    public double getTotalProfitLossPercent() {
        return totalProfitLossPercent;
    }
}
