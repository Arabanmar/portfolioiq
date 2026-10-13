package com.portfolioiq.domain.repository;

import com.portfolioiq.domain.model.AnalysisProgress;
import com.portfolioiq.domain.model.HoldingPosition;

import java.util.List;

/** Hand-written test double for AiAnalysisRepository (no real AI server). */
public class FakeAiAnalysisRepository implements AiAnalysisRepository {

    public String jobIdToReturn = "job-1";
    public AnalysisProgress progressToReturn;
    public String answerToReturn = "Your biggest risk is concentration.";
    public String errorToReturn = null;

    public boolean startCalled = false;
    public List<HoldingPosition> lastPositions;
    public boolean progressCalled = false;
    public String lastJobId;
    public boolean askCalled = false;
    public String lastQuestion;

    @Override
    public void startAnalysis(List<HoldingPosition> positions, ResultCallback<String> callback) {
        startCalled = true;
        lastPositions = positions;
        if (errorToReturn != null) callback.onError(errorToReturn);
        else callback.onSuccess(jobIdToReturn);
    }

    @Override
    public void getProgress(String jobId, ResultCallback<AnalysisProgress> callback) {
        progressCalled = true;
        lastJobId = jobId;
        if (errorToReturn != null) callback.onError(errorToReturn);
        else callback.onSuccess(progressToReturn);
    }

    @Override
    public void askQuestion(String jobId, String question, ResultCallback<String> callback) {
        askCalled = true;
        lastJobId = jobId;
        lastQuestion = question;
        if (errorToReturn != null) callback.onError(errorToReturn);
        else callback.onSuccess(answerToReturn);
    }
}
