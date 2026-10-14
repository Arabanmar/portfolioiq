package com.portfolioiq.domain.usecase;

import com.portfolioiq.domain.model.AnalysisProgress;
import com.portfolioiq.domain.repository.AiAnalysisRepository;
import com.portfolioiq.domain.repository.ResultCallback;

/** Story 005: check how far the running analysis has got. */
public class GetAnalysisProgressUseCase {

    private final AiAnalysisRepository aiAnalysisRepository;

    public GetAnalysisProgressUseCase(AiAnalysisRepository aiAnalysisRepository) {
        this.aiAnalysisRepository = aiAnalysisRepository;
    }

    public void execute(String jobId, ResultCallback<AnalysisProgress> callback) {
        if (jobId == null || jobId.trim().isEmpty()) {
            callback.onError("No analysis is running. Start the AI analysis again.");
            return;
        }
        aiAnalysisRepository.getProgress(jobId.trim(), callback);
    }
}
