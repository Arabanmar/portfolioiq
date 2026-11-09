package com.portfolioiq.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.portfolioiq.domain.model.AgentConfidence;
import com.portfolioiq.domain.model.AgentDebate;
import com.portfolioiq.domain.model.AgentReading;
import com.portfolioiq.domain.model.AnalysisProgress;
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

    private static final String PLAIN_RISK = "{\"riskPercent\":54.5,\"riskLevel\":\"Medium\"}";

    /** A finished analysis as the server sends it, with the given "risk" object. */
    private static JSONObject result(String riskJson) throws Exception {
        return result(riskJson, "", "");
    }

    /**
     * Same, with extra JSON members: extraResult is added to the result and
     * extraStock to its one stock. Each must start with a comma (or be empty).
     */
    private static JSONObject result(String riskJson, String extraResult, String extraStock)
            throws Exception {
        return new JSONObject("{"
                + "\"analysisDate\":\"2026-11-08\","
                + "\"metrics\":{\"diversificationScore\":0.0,\"largestPosition\":\"AAPL\","
                + "\"largestPositionPercent\":100.0},"
                + "\"risk\":" + riskJson + ","
                + "\"summary\":{\"summary\":\"s\",\"strengths\":[\"a\"],\"weaknesses\":[\"b\"],"
                + "\"diversificationComment\":\"c\"},"
                + "\"stocks\":[{\"ticker\":\"AAPL\",\"rating\":\"Buy\",\"decision\":\"d\""
                + extraStock + "}]"
                + extraResult
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

    @Test
    public void confidence_isParsedForEachStockAndForThePortfolio() throws Exception {
        PortfolioAnalysis analysis = HttpAiAnalysisRepositoryImpl.parseAnalysis(result(PLAIN_RISK,
                ",\"confidence\":{\"confidencePercent\":77.5,\"confidenceLevel\":\"High\"}",
                ",\"confidencePercent\":90.0,\"confidenceLevel\":\"High\","
                        + "\"votes\":{\"researchManager\":\"Buy\",\"trader\":\"Buy\",\"final\":\"Buy\"}"));

        AgentConfidence portfolio = analysis.getConfidence();
        assertNotNull(portfolio);
        assertEquals(77.5, portfolio.getPercent(), DELTA);
        assertEquals("High", portfolio.getLevel());

        AgentConfidence stock = analysis.getStocks().get(0).getConfidence();
        assertNotNull(stock);
        assertEquals(90.0, stock.getPercent(), DELTA);
        assertEquals("High", stock.getLevel());
        // the rest of the stock still reads as before
        assertEquals("Buy", analysis.getStocks().get(0).getRating());
        assertEquals("d", analysis.getStocks().get(0).getDecision());
    }

    @Test
    public void lowConfidence_isParsedAsGiven() throws Exception {
        PortfolioAnalysis analysis = HttpAiAnalysisRepositoryImpl.parseAnalysis(result(PLAIN_RISK, "",
                ",\"confidencePercent\":10.0,\"confidenceLevel\":\"Low\""));

        assertEquals(10.0, analysis.getStocks().get(0).getConfidence().getPercent(), DELTA);
        assertEquals("Low", analysis.getStocks().get(0).getConfidence().getLevel());
    }

    @Test
    public void olderServerWithoutConfidence_hasNone() throws Exception {
        PortfolioAnalysis analysis = HttpAiAnalysisRepositoryImpl.parseAnalysis(result(PLAIN_RISK));

        assertNull(analysis.getConfidence());
        assertNull(analysis.getStocks().get(0).getConfidence());
        assertEquals("Buy", analysis.getStocks().get(0).getRating());
    }

    @Test
    public void nullPortfolioConfidence_hasNone() throws Exception {
        PortfolioAnalysis analysis = HttpAiAnalysisRepositoryImpl.parseAnalysis(
                result(PLAIN_RISK, ",\"confidence\":null", ""));

        assertNull(analysis.getConfidence());
    }

    @Test
    public void confidenceMissingItsLevel_hasNone() throws Exception {
        PortfolioAnalysis analysis = HttpAiAnalysisRepositoryImpl.parseAnalysis(result(PLAIN_RISK,
                ",\"confidence\":{\"confidencePercent\":77.5}", ",\"confidencePercent\":90.0"));

        assertNull(analysis.getConfidence());
        assertNull(analysis.getStocks().get(0).getConfidence());
    }

    private static final String TWO_SOURCES =
            "\"sources\":[\"Yahoo Finance prices and indicators\",\"SEC EDGAR financial statements\"]";

    @Test
    public void reading_isParsedForEachStock() throws Exception {
        PortfolioAnalysis analysis = HttpAiAnalysisRepositoryImpl.parseAnalysis(result(PLAIN_RISK, "",
                ",\"newsSummary\":\"Shares rose.\\nCosts fell.\"," + TWO_SOURCES));

        AgentReading reading = analysis.getStocks().get(0).getReading();
        assertNotNull(reading);
        assertEquals("Shares rose.\nCosts fell.", reading.getNewsSummary());
        assertEquals(2, reading.getSources().size());
        assertEquals("Yahoo Finance prices and indicators", reading.getSources().get(0));
        assertEquals("SEC EDGAR financial statements", reading.getSources().get(1));
        // the rest of the stock still reads as before
        assertEquals("Buy", analysis.getStocks().get(0).getRating());
        assertEquals("d", analysis.getStocks().get(0).getDecision());
    }

    @Test
    public void olderServerWithoutReading_hasNone() throws Exception {
        PortfolioAnalysis analysis = HttpAiAnalysisRepositoryImpl.parseAnalysis(result(PLAIN_RISK));

        assertNull(analysis.getStocks().get(0).getReading());
        assertEquals("Buy", analysis.getStocks().get(0).getRating());
    }

    @Test
    public void sourcesWithoutNews_areKeptWithAnEmptySummary() throws Exception {
        PortfolioAnalysis analysis = HttpAiAnalysisRepositoryImpl.parseAnalysis(result(PLAIN_RISK, "",
                ",\"newsSummary\":\"\"," + TWO_SOURCES));

        AgentReading reading = analysis.getStocks().get(0).getReading();
        assertNotNull(reading);
        assertEquals("", reading.getNewsSummary());
        assertEquals(2, reading.getSources().size());
    }

    @Test
    public void newsWithoutSources_isKeptWithNoSources() throws Exception {
        PortfolioAnalysis analysis = HttpAiAnalysisRepositoryImpl.parseAnalysis(result(PLAIN_RISK, "",
                ",\"newsSummary\":\"Quiet week.\",\"sources\":[]"));

        AgentReading reading = analysis.getStocks().get(0).getReading();
        assertNotNull(reading);
        assertEquals("Quiet week.", reading.getNewsSummary());
        assertTrue(reading.getSources().isEmpty());
    }

    @Test
    public void blankAndNullSources_areDropped() throws Exception {
        PortfolioAnalysis analysis = HttpAiAnalysisRepositoryImpl.parseAnalysis(result(PLAIN_RISK, "",
                ",\"sources\":[\" \",null,\"Yahoo Finance news, past 7 days\"]"));

        AgentReading reading = analysis.getStocks().get(0).getReading();
        assertNotNull(reading);
        assertEquals(1, reading.getSources().size());
        assertEquals("Yahoo Finance news, past 7 days", reading.getSources().get(0));
    }

    @Test
    public void nullNewsAndNoSources_hasNoReading() throws Exception {
        PortfolioAnalysis analysis = HttpAiAnalysisRepositoryImpl.parseAnalysis(result(PLAIN_RISK, "",
                ",\"newsSummary\":null,\"sources\":[]"));

        assertNull(analysis.getStocks().get(0).getReading());
    }

    @Test
    public void readingAndConfidence_areParsedTogether() throws Exception {
        PortfolioAnalysis analysis = HttpAiAnalysisRepositoryImpl.parseAnalysis(result(PLAIN_RISK, "",
                ",\"confidencePercent\":90.0,\"confidenceLevel\":\"High\","
                        + "\"newsSummary\":\"Shares rose.\"," + TWO_SOURCES));

        assertNotNull(analysis.getStocks().get(0).getConfidence());
        assertNotNull(analysis.getStocks().get(0).getReading());
        assertEquals("Shares rose.", analysis.getStocks().get(0).getReading().getNewsSummary());
    }

    @Test
    public void debate_isParsedForEachStock() throws Exception {
        PortfolioAnalysis analysis = HttpAiAnalysisRepositoryImpl.parseAnalysis(result(PLAIN_RISK, "",
                ",\"bullCase\":\"Growth is strong.\",\"bearCase\":\"Costs are rising.\""));

        AgentDebate debate = analysis.getStocks().get(0).getDebate();
        assertNotNull(debate);
        assertEquals("Growth is strong.", debate.getBullCase());
        assertEquals("Costs are rising.", debate.getBearCase());
        // the rest of the stock still reads as before
        assertEquals("Buy", analysis.getStocks().get(0).getRating());
        assertEquals("d", analysis.getStocks().get(0).getDecision());
    }

    @Test
    public void debateLineBreaks_areKept() throws Exception {
        PortfolioAnalysis analysis = HttpAiAnalysisRepositoryImpl.parseAnalysis(result(PLAIN_RISK, "",
                ",\"bullCase\":\"First.\\nSecond.\",\"bearCase\":\"Third.\""));

        assertEquals("First.\nSecond.", analysis.getStocks().get(0).getDebate().getBullCase());
    }

    @Test
    public void olderServerWithoutDebate_hasNone() throws Exception {
        PortfolioAnalysis analysis = HttpAiAnalysisRepositoryImpl.parseAnalysis(result(PLAIN_RISK));

        assertNull(analysis.getStocks().get(0).getDebate());
        assertEquals("Buy", analysis.getStocks().get(0).getRating());
    }

    @Test
    public void oneSideOnly_isKeptWithTheOtherEmpty() throws Exception {
        PortfolioAnalysis bullOnly = HttpAiAnalysisRepositoryImpl.parseAnalysis(
                result(PLAIN_RISK, "", ",\"bullCase\":\"Growth is strong.\""));
        PortfolioAnalysis bearOnly = HttpAiAnalysisRepositoryImpl.parseAnalysis(
                result(PLAIN_RISK, "", ",\"bullCase\":\"\",\"bearCase\":\"Costs are rising.\""));

        assertEquals("Growth is strong.", bullOnly.getStocks().get(0).getDebate().getBullCase());
        assertEquals("", bullOnly.getStocks().get(0).getDebate().getBearCase());
        assertEquals("", bearOnly.getStocks().get(0).getDebate().getBullCase());
        assertEquals("Costs are rising.", bearOnly.getStocks().get(0).getDebate().getBearCase());
    }

    @Test
    public void blankOrNullCases_haveNoDebate() throws Exception {
        PortfolioAnalysis analysis = HttpAiAnalysisRepositoryImpl.parseAnalysis(result(PLAIN_RISK, "",
                ",\"bullCase\":\"   \",\"bearCase\":null"));

        assertNull(analysis.getStocks().get(0).getDebate());
    }

    @Test
    public void debateReadingAndConfidence_areParsedTogether() throws Exception {
        PortfolioAnalysis analysis = HttpAiAnalysisRepositoryImpl.parseAnalysis(result(PLAIN_RISK, "",
                ",\"confidencePercent\":90.0,\"confidenceLevel\":\"High\","
                        + "\"newsSummary\":\"Shares rose.\",\"sources\":[\"SEC EDGAR financial statements\"],"
                        + "\"bullCase\":\"Growth is strong.\",\"bearCase\":\"Costs are rising.\""));

        assertNotNull(analysis.getStocks().get(0).getConfidence());
        assertNotNull(analysis.getStocks().get(0).getReading());
        assertNotNull(analysis.getStocks().get(0).getDebate());
        assertEquals("Costs are rising.", analysis.getStocks().get(0).getDebate().getBearCase());
    }

    // ---- saved results (Issue E) ----

    private static JSONObject runningJob(String progressJson) throws Exception {
        return new JSONObject("{\"status\":\"running\",\"progress\":" + progressJson + "}");
    }

    @Test
    public void progress_saysWhenTheCurrentStockIsASavedResult() throws Exception {
        AnalysisProgress progress = HttpAiAnalysisRepositoryImpl.parseProgress(runningJob(
                "{\"done\":1,\"total\":3,\"current\":\"MSFT\",\"currentFromCache\":true}"));

        assertEquals(AnalysisProgress.Status.RUNNING, progress.getStatus());
        assertEquals("MSFT", progress.getCurrentTicker());
        assertEquals(1, progress.getStocksDone());
        assertEquals(3, progress.getStocksTotal());
        assertTrue(progress.isCurrentFromCache());
    }

    @Test
    public void progress_whenTheAgentsRun_isNotASavedResult() throws Exception {
        AnalysisProgress progress = HttpAiAnalysisRepositoryImpl.parseProgress(runningJob(
                "{\"done\":0,\"total\":2,\"current\":\"AAPL\",\"currentFromCache\":false}"));

        assertEquals("AAPL", progress.getCurrentTicker());
        assertFalse(progress.isCurrentFromCache());
    }

    @Test
    public void progress_fromAnOlderServerWithoutTheFlag_isNotASavedResult() throws Exception {
        AnalysisProgress progress = HttpAiAnalysisRepositoryImpl.parseProgress(runningJob(
                "{\"done\":0,\"total\":2,\"current\":\"AAPL\"}"));

        assertFalse(progress.isCurrentFromCache());
        assertEquals("AAPL", progress.getCurrentTicker());
    }

    @Test
    public void progress_withANullFlagAndNoCurrentStock_isNotASavedResult() throws Exception {
        AnalysisProgress progress = HttpAiAnalysisRepositoryImpl.parseProgress(runningJob(
                "{\"done\":2,\"total\":2,\"current\":null,\"currentFromCache\":null}"));

        assertNull(progress.getCurrentTicker());
        assertFalse(progress.isCurrentFromCache());
    }

    @Test
    public void progress_whenDone_carriesTheAnalysisAndNoSavedFlag() throws Exception {
        JSONObject job = new JSONObject("{\"status\":\"done\","
                + "\"progress\":{\"done\":1,\"total\":1,\"current\":null,\"currentFromCache\":false},"
                + "\"result\":" + result(PLAIN_RISK, "", ",\"fromCache\":true") + "}");

        AnalysisProgress progress = HttpAiAnalysisRepositoryImpl.parseProgress(job);

        assertEquals(AnalysisProgress.Status.DONE, progress.getStatus());
        assertFalse(progress.isCurrentFromCache());
        assertTrue(progress.getAnalysis().getStocks().get(0).isFromCache());
    }

    @Test
    public void stockFromCache_isParsed() throws Exception {
        PortfolioAnalysis saved = HttpAiAnalysisRepositoryImpl.parseAnalysis(
                result(PLAIN_RISK, "", ",\"fromCache\":true"));
        PortfolioAnalysis fresh = HttpAiAnalysisRepositoryImpl.parseAnalysis(
                result(PLAIN_RISK, "", ",\"fromCache\":false"));

        assertTrue(saved.getStocks().get(0).isFromCache());
        assertFalse(fresh.getStocks().get(0).isFromCache());
    }

    @Test
    public void stockFromAnOlderServerWithoutTheFlag_isNotFromCache() throws Exception {
        PortfolioAnalysis analysis = HttpAiAnalysisRepositoryImpl.parseAnalysis(result(PLAIN_RISK));

        assertFalse(analysis.getStocks().get(0).isFromCache());
        assertEquals("Buy", analysis.getStocks().get(0).getRating());
    }

    @Test
    public void stockWithANullFromCache_isNotFromCache() throws Exception {
        PortfolioAnalysis analysis = HttpAiAnalysisRepositoryImpl.parseAnalysis(
                result(PLAIN_RISK, "", ",\"fromCache\":null"));

        assertFalse(analysis.getStocks().get(0).isFromCache());
    }
}
