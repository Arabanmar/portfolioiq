package com.portfolioiq.domain.usecase;

import com.portfolioiq.domain.repository.AiAnalysisRepository;
import com.portfolioiq.domain.repository.ResultCallback;

/**
 * Story 006: ask the AI assistant a question about the analysed portfolio.
 * Raw text straight off the chat box, validated here like every use case.
 */
public class AskPortfolioQuestionUseCase {

    static final int MAX_QUESTION_LENGTH = 500;

    private final AiAnalysisRepository aiAnalysisRepository;

    public AskPortfolioQuestionUseCase(AiAnalysisRepository aiAnalysisRepository) {
        this.aiAnalysisRepository = aiAnalysisRepository;
    }

    public void execute(String jobId, String questionText, ResultCallback<String> callback) {
        if (jobId == null || jobId.trim().isEmpty()) {
            callback.onError("Run the AI analysis first, then ask your question.");
            return;
        }
        if (questionText == null || questionText.trim().isEmpty()) {
            callback.onError("Type a question first.");
            return;
        }
        String question = questionText.trim();
        if (question.length() > MAX_QUESTION_LENGTH) {
            callback.onError("Keep your question under " + MAX_QUESTION_LENGTH + " characters.");
            return;
        }
        aiAnalysisRepository.askQuestion(jobId.trim(), question, callback);
    }
}
