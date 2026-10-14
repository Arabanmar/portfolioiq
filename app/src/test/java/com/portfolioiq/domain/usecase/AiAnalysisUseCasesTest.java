package com.portfolioiq.domain.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.portfolioiq.domain.model.AnalysisProgress;
import com.portfolioiq.domain.model.HoldingPosition;
import com.portfolioiq.domain.model.PortfolioSummary;
import com.portfolioiq.domain.model.StockHolding;
import com.portfolioiq.domain.repository.FakeAiAnalysisRepository;
import com.portfolioiq.domain.repository.ResultCallback;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Stories 005/006: the three AI analysis use cases (TradingAgents via the AI server). */
public class AiAnalysisUseCasesTest {

    private FakeAiAnalysisRepository fakeRepository;

    @Before
    public void setUp() {
        fakeRepository = new FakeAiAnalysisRepository();
    }

    private static PortfolioSummary portfolioOf(int stocks) {
        List<HoldingPosition> positions = new ArrayList<>();
        for (int i = 0; i < stocks; i++) {
            StockHolding h = new StockHolding("id" + i, "T" + i, 1, 100);
            positions.add(new HoldingPosition(h, 100, true, 100, 0, 0, 100.0 / stocks));
        }
        return new PortfolioSummary(positions, 100.0 * stocks, 100.0 * stocks, 0, 0);
    }

    // --- StartPortfolioAnalysisUseCase ---

    @Test
    public void emptyPortfolio_isNotSentForAnalysis() {
        Result<String> r = new Result<>();
        new StartPortfolioAnalysisUseCase(fakeRepository).execute(PortfolioSummary.empty(), r);

        assertEquals("Add at least one stock before running the AI analysis.", r.error);
        assertFalse(fakeRepository.startCalled);
    }

    @Test
    public void tooManyStocks_isNotSentForAnalysis() {
        Result<String> r = new Result<>();
        new StartPortfolioAnalysisUseCase(fakeRepository).execute(portfolioOf(11), r);

        assertEquals("The AI analysis can handle up to 10 stocks at a time.", r.error);
        assertFalse(fakeRepository.startCalled);
    }

    @Test
    public void validPortfolio_startsAnalysisWithAllPositions() {
        PortfolioSummary portfolio = portfolioOf(3);
        Result<String> r = new Result<>();
        new StartPortfolioAnalysisUseCase(fakeRepository).execute(portfolio, r);

        assertTrue(fakeRepository.startCalled);
        assertEquals(3, fakeRepository.lastPositions.size());
        assertEquals("job-1", r.value);
    }

    @Test
    public void serverError_isPassedThrough() {
        fakeRepository.errorToReturn = "Can't reach the AI server.";
        Result<String> r = new Result<>();
        new StartPortfolioAnalysisUseCase(fakeRepository).execute(portfolioOf(1), r);

        assertEquals("Can't reach the AI server.", r.error);
    }

    // --- GetAnalysisProgressUseCase ---

    @Test
    public void missingJobId_doesNotCallServer() {
        Result<AnalysisProgress> r = new Result<>();
        new GetAnalysisProgressUseCase(fakeRepository).execute(" ", r);

        assertEquals("No analysis is running. Start the AI analysis again.", r.error);
        assertFalse(fakeRepository.progressCalled);
    }

    @Test
    public void progress_isReturnedForTrimmedJobId() {
        AnalysisProgress running = new AnalysisProgress(AnalysisProgress.Status.RUNNING, 1, 3, "MSFT", null, null);
        fakeRepository.progressToReturn = running;
        Result<AnalysisProgress> r = new Result<>();
        new GetAnalysisProgressUseCase(fakeRepository).execute(" job-1 ", r);

        assertEquals("job-1", fakeRepository.lastJobId);
        assertSame(running, r.value);
    }

    // --- AskPortfolioQuestionUseCase ---

    @Test
    public void blankQuestion_isRejected() {
        Result<String> r = new Result<>();
        new AskPortfolioQuestionUseCase(fakeRepository).execute("job-1", "   ", r);

        assertEquals("Type a question first.", r.error);
        assertFalse(fakeRepository.askCalled);
    }

    @Test
    public void questionWithoutAnalysis_isRejected() {
        Result<String> r = new Result<>();
        new AskPortfolioQuestionUseCase(fakeRepository).execute(null, "Is it risky?", r);

        assertEquals("Run the AI analysis first, then ask your question.", r.error);
        assertFalse(fakeRepository.askCalled);
    }

    @Test
    public void tooLongQuestion_isRejected() {
        StringBuilder longQuestion = new StringBuilder();
        for (int i = 0; i < 501; i++) longQuestion.append('a');
        Result<String> r = new Result<>();
        new AskPortfolioQuestionUseCase(fakeRepository).execute("job-1", longQuestion.toString(), r);

        assertEquals("Keep your question under 500 characters.", r.error);
        assertFalse(fakeRepository.askCalled);
    }

    @Test
    public void validQuestion_isTrimmedAndAnswered() {
        Result<String> r = new Result<>();
        new AskPortfolioQuestionUseCase(fakeRepository).execute("job-1", "  Which stock is riskiest?  ", r);

        assertEquals("Which stock is riskiest?", fakeRepository.lastQuestion);
        assertEquals("Your biggest risk is concentration.", r.value);
        assertNull(r.error);
    }

    private static class Result<T> implements ResultCallback<T> {
        T value;
        String error;

        @Override
        public void onSuccess(T result) {
            value = result;
        }

        @Override
        public void onError(String message) {
            error = message;
        }
    }
}
