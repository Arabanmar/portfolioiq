package com.portfolioiq.domain.model;

/**
 * The three parts of the AI analysis's risk score, each 0-100. Calculated by
 * code on the AI server (never by the language model); the app only shows them.
 *
 * annualVolatilityPercent is null when the stocks' price history could not be
 * downloaded; the server then uses a neutral 50 for the volatility part and
 * sets volatilityAvailable to false.
 */
public class RiskBreakdown {

    private final double volatilityPart;
    private final double concentrationPart;
    private final double ratingsPart;
    private final Double annualVolatilityPercent;
    private final boolean volatilityAvailable;

    public RiskBreakdown(double volatilityPart, double concentrationPart, double ratingsPart,
                         Double annualVolatilityPercent, boolean volatilityAvailable) {
        this.volatilityPart = volatilityPart;
        this.concentrationPart = concentrationPart;
        this.ratingsPart = ratingsPart;
        this.annualVolatilityPercent = annualVolatilityPercent;
        this.volatilityAvailable = volatilityAvailable;
    }

    public double getVolatilityPart() { return volatilityPart; }
    public double getConcentrationPart() { return concentrationPart; }
    public double getRatingsPart() { return ratingsPart; }
    public Double getAnnualVolatilityPercent() { return annualVolatilityPercent; }
    public boolean isVolatilityAvailable() { return volatilityAvailable; }
}
