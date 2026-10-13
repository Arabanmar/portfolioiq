package com.portfolioiq.domain.model;

import java.util.Collections;
import java.util.List;

/**
 * The finished AI analysis of a portfolio (Stories 005/006, doctor's choice:
 * TradingAgents). All percentages were calculated by code on the AI server,
 * not by the language model; the model only wrote the text fields.
 */
public class PortfolioAnalysis {

    private final String analysisDate;
    private final double riskPercent;
    private final String riskLevel;
    private final double diversificationScore;
    private final String largestPosition;
    private final double largestPositionPercent;
    private final String summary;
    private final List<String> strengths;
    private final List<String> weaknesses;
    private final String diversificationComment;
    private final List<StockAnalysis> stocks;

    public PortfolioAnalysis(String analysisDate, double riskPercent, String riskLevel,
                             double diversificationScore, String largestPosition,
                             double largestPositionPercent, String summary, List<String> strengths,
                             List<String> weaknesses, String diversificationComment,
                             List<StockAnalysis> stocks) {
        this.analysisDate = analysisDate;
        this.riskPercent = riskPercent;
        this.riskLevel = riskLevel;
        this.diversificationScore = diversificationScore;
        this.largestPosition = largestPosition;
        this.largestPositionPercent = largestPositionPercent;
        this.summary = summary;
        this.strengths = Collections.unmodifiableList(strengths);
        this.weaknesses = Collections.unmodifiableList(weaknesses);
        this.diversificationComment = diversificationComment;
        this.stocks = Collections.unmodifiableList(stocks);
    }

    public String getAnalysisDate() { return analysisDate; }
    public double getRiskPercent() { return riskPercent; }
    public String getRiskLevel() { return riskLevel; }
    public double getDiversificationScore() { return diversificationScore; }
    public String getLargestPosition() { return largestPosition; }
    public double getLargestPositionPercent() { return largestPositionPercent; }
    public String getSummary() { return summary; }
    public List<String> getStrengths() { return strengths; }
    public List<String> getWeaknesses() { return weaknesses; }
    public String getDiversificationComment() { return diversificationComment; }
    public List<StockAnalysis> getStocks() { return stocks; }
}
