package com.portfolioiq.domain.repository;

import com.portfolioiq.domain.model.AnalysisProgress;
import com.portfolioiq.domain.model.HoldingPosition;

import java.util.List;

/**
 * Domain-facing contract for the AI analysis (Stories 005/006). The
 * implementation talks to the PortfolioIQ AI server (ai-server/ in this
 * repository), which runs TradingAgents with Gemini. Every error message
 * passed to onError is already safe to show the user.
 */
public interface AiAnalysisRepository {

    /** Starts analysing these positions. onSuccess receives the analysis job id. */
    void startAnalysis(List<HoldingPosition> positions, ResultCallback<String> callback);

    void getProgress(String jobId, ResultCallback<AnalysisProgress> callback);

    /** Asks a question about a finished analysis. onSuccess receives the answer text. */
    void askQuestion(String jobId, String question, ResultCallback<String> callback);
}
