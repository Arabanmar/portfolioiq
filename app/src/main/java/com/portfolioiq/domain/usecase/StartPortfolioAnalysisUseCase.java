package com.portfolioiq.domain.usecase;

import com.portfolioiq.domain.model.PortfolioSummary;
import com.portfolioiq.domain.repository.AiAnalysisRepository;
import com.portfolioiq.domain.repository.ResultCallback;

/**
 * Story 005: start the TradingAgents analysis of the user's portfolio. Takes
 * the PortfolioSummary GetPortfolioUseCase already built, so the server gets
 * the same quantities and live prices the dashboard shows.
 */
public class StartPortfolioAnalysisUseCase {

    /** Matches the server's limit; each stock takes minutes of AI calls. */
    static final int MAX_STOCKS = 10;

    private final AiAnalysisRepository aiAnalysisRepository;

    public StartPortfolioAnalysisUseCase(AiAnalysisRepository aiAnalysisRepository) {
        this.aiAnalysisRepository = aiAnalysisRepository;
    }

    public void execute(PortfolioSummary portfolio, ResultCallback<String> callback) {
        if (portfolio == null || portfolio.getPositions().isEmpty()) {
            callback.onError("Add at least one stock before running the AI analysis.");
            return;
        }
        if (portfolio.getPositions().size() > MAX_STOCKS) {
            callback.onError("The AI analysis can handle up to " + MAX_STOCKS + " stocks at a time.");
            return;
        }
        aiAnalysisRepository.startAnalysis(portfolio.getPositions(), callback);
    }
}
