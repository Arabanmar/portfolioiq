package com.portfolioiq.domain.model;

/**
 * One stock the user can pick from the ticker suggestions on the Add/Edit
 * Holding screen, e.g. symbol "AAPL", description "APPLE INC". Immutable,
 * like StockHolding.
 */
public class StockSymbol {

    private final String symbol;
    private final String description;

    public StockSymbol(String symbol, String description) {
        this.symbol = symbol;
        this.description = description;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getDescription() {
        return description;
    }

    /** What the suggestion list shows, e.g. "AAPL - APPLE INC". */
    public String getDisplayText() {
        return description == null || description.isEmpty() ? symbol : symbol + " - " + description;
    }
}
