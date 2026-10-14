package com.portfolioiq.domain.model;

/**
 * Where a running AI analysis is. TradingAgents takes a few minutes per
 * stock, so the app checks this every few seconds until it is DONE or FAILED.
 */
public class AnalysisProgress {

    public enum Status { RUNNING, DONE, FAILED }

    private final Status status;
    private final int stocksDone;
    private final int stocksTotal;
    private final String currentTicker;   // null when no stock is being analysed right now
    private final PortfolioAnalysis analysis; // only when DONE
    private final String error;            // only when FAILED, already safe to show

    public AnalysisProgress(Status status, int stocksDone, int stocksTotal, String currentTicker,
                            PortfolioAnalysis analysis, String error) {
        this.status = status;
        this.stocksDone = stocksDone;
        this.stocksTotal = stocksTotal;
        this.currentTicker = currentTicker;
        this.analysis = analysis;
        this.error = error;
    }

    public Status getStatus() { return status; }
    public int getStocksDone() { return stocksDone; }
    public int getStocksTotal() { return stocksTotal; }
    public String getCurrentTicker() { return currentTicker; }
    public PortfolioAnalysis getAnalysis() { return analysis; }
    public String getError() { return error; }
}
