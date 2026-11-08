package com.portfolioiq.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.portfolioiq.domain.model.PortfolioAnalysis;
import com.portfolioiq.domain.model.RiskBreakdown;

import org.json.JSONObject;
import org.junit.Test;

/**
 * Reading the AI server's finished-analysis JSON (Issue A: the three risk
 * parts). Runs on the plain JVM with the real org.json (see build.gradle.kts).
 */
public class HttpAiAnalysisParsingTest {

    private static final double DELTA = 0.0001;

    /** A finished analysis as the server sends it, with the given "risk" object. */
    private static JSONObject result(String riskJson) throws Exception {
        return new JSONObject("{"
                + "\"analysisDate\":\"2026-11-08\","
                + "\"metrics\":{\"diversificationScore\":0.0,\"largestPosition\":\"AAPL\","
                + "\"largestPositionPercent\":100.0},"
                + "\"risk\":" + riskJson + ","
                + "\"summary\":{\"summary\":\"s\",\"strengths\":[\"a\"],\"weaknesses\":[\"b\"],"
                + "\"diversificationComment\":\"c\"},"
                + "\"stocks\":[{\"ticker\":\"AAPL\",\"rating\":\"Buy\",\"decision\":\"d\"}]"
                + "}");
    }

    @Test
    public void riskParts_areParsed() throws Exception {
        PortfolioAnalysis analysis = HttpAiAnalysisRepositoryImpl.parseAnalysis(result(
                "{\"riskPercent\":54.5,\"riskLevel\":\"Medium\",\"volatilityPart\":50.0,"
                        + "\"concentrationPart\":100.0,\"ratingsPart\":15.0,"
                        + "\"annualVolatilityPercent\":25.0,\"volatilityAvailable\":true}"));

        assertEquals(54.5, analysis.getRiskPercent(), DELTA);
        assertEquals("Medium", analysis.getRiskLevel());
        RiskBreakdown parts = analysis.getRiskBreakdown();
        assertNotNull(parts);
        assertEquals(50.0, parts.getVolatilityPart(), DELTA);
        assertEquals(100.0, parts.getConcentrationPart(), DELTA);
        assertEquals(15.0, parts.getRatingsPart(), DELTA);
        assertEquals(25.0, parts.getAnnualVolatilityPercent(), DELTA);
        assertTrue(parts.isVolatilityAvailable());
        // the rest of the analysis still reads as before
        assertEquals("AAPL", analysis.getStocks().get(0).getTicker());
        assertEquals("Buy", analysis.getStocks().get(0).getRating());
    }

    @Test
    public void unavailableVolatility_hasNoAnnualFigureAndIsFlagged() throws Exception {
        PortfolioAnalysis analysis = HttpAiAnalysisRepositoryImpl.parseAnalysis(result(
                "{\"riskPercent\":54.5,\"riskLevel\":\"Medium\",\"volatilityPart\":50.0,"
                        + "\"concentrationPart\":100.0,\"ratingsPart\":15.0,"
                        + "\"annualVolatilityPercent\":null,\"volatilityAvailable\":false}"));

        RiskBreakdown parts = analysis.getRiskBreakdown();
        assertNotNull(parts);
        assertEquals(50.0, parts.getVolatilityPart(), DELTA);
        assertNull(parts.getAnnualVolatilityPercent());
        assertFalse(parts.isVolatilityAvailable());
    }

    @Test
    public void olderServerWithoutTheNewFields_stillParsesWithNoBreakdown() throws Exception {
        PortfolioAnalysis analysis = HttpAiAnalysisRepositoryImpl.parseAnalysis(result(
                "{\"riskPercent\":43.75,\"riskLevel\":\"Medium\","
                        + "\"concentrationPart\":62.5,\"ratingsPart\":25.0}"));

        assertEquals(43.75, analysis.getRiskPercent(), DELTA);
        assertNull(analysis.getRiskBreakdown());
    }

    @Test
    public void missingAvailabilityFlag_countsAsNotAvailable() throws Exception {
        PortfolioAnalysis analysis = HttpAiAnalysisRepositoryImpl.parseAnalysis(result(
                "{\"riskPercent\":54.5,\"riskLevel\":\"Medium\",\"volatilityPart\":50.0,"
                        + "\"concentrationPart\":100.0,\"ratingsPart\":15.0}"));

        RiskBreakdown parts = analysis.getRiskBreakdown();
        assertNotNull(parts);
        assertNull(parts.getAnnualVolatilityPercent());
        assertFalse(parts.isVolatilityAvailable());
    }
}
