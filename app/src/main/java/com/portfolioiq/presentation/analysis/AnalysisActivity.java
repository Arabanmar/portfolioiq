package com.portfolioiq.presentation.analysis;

import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;

import com.portfolioiq.R;
import com.portfolioiq.data.repository.FinnhubStockPriceRepositoryImpl;
import com.portfolioiq.data.repository.FirestorePortfolioRepositoryImpl;
import com.portfolioiq.data.repository.HttpAiAnalysisRepositoryImpl;
import com.portfolioiq.domain.model.AnalysisProgress;
import com.portfolioiq.domain.model.PortfolioAnalysis;
import com.portfolioiq.domain.model.PortfolioSummary;
import com.portfolioiq.domain.model.StockAnalysis;
import com.portfolioiq.domain.repository.AiAnalysisRepository;
import com.portfolioiq.domain.repository.ResultCallback;
import com.portfolioiq.domain.usecase.GetAnalysisProgressUseCase;
import com.portfolioiq.domain.usecase.GetPortfolioUseCase;
import com.portfolioiq.domain.usecase.StartPortfolioAnalysisUseCase;

import java.util.List;
import java.util.Locale;

/**
 * Story 005: AI Portfolio Analysis with TradingAgents (the doctor's choice).
 *
 * Loads the portfolio the same way the dashboard does, sends it to the AI
 * server, then checks progress every few seconds until the analysis is done
 * and shows: risk %, diversification, summary, strengths, weaknesses and
 * each stock's rating. Only displays what the use cases return.
 */
public class AnalysisActivity extends AppCompatActivity {

    private static final long POLL_INTERVAL_MS = 4000;
    private static final String STATE_JOB_ID = "job_id";
    private static final int DECISION_PREVIEW_CHARS = 600;
    /** A slow laptop can miss one progress check while the agents are busy; give up only after several in a row. */
    private static final int MAX_POLL_FAILURES = 5;

    private GetPortfolioUseCase getPortfolioUseCase;
    private StartPortfolioAnalysisUseCase startAnalysisUseCase;
    private GetAnalysisProgressUseCase getProgressUseCase;

    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private String jobId;
    private boolean jobFailed = false;
    private int pollFailures = 0;
    private boolean destroyed = false;

    private View progressSection;
    private View resultSection;
    private TextView statusText;
    private TextView errorText;
    private Button retryButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analysis);

        AiAnalysisRepository aiRepository = new HttpAiAnalysisRepositoryImpl();
        getPortfolioUseCase = new GetPortfolioUseCase(
                new FirestorePortfolioRepositoryImpl(), new FinnhubStockPriceRepositoryImpl());
        startAnalysisUseCase = new StartPortfolioAnalysisUseCase(aiRepository);
        getProgressUseCase = new GetAnalysisProgressUseCase(aiRepository);

        progressSection = findViewById(R.id.progressSection);
        resultSection = findViewById(R.id.resultSection);
        statusText = findViewById(R.id.statusText);
        errorText = findViewById(R.id.errorText);
        retryButton = findViewById(R.id.retryButton);
        Button askButton = findViewById(R.id.askButton);

        retryButton.setOnClickListener(v -> {
            if (jobId != null && !jobFailed) {
                // The analysis may still be running on the server: check it again
                // instead of starting a new one from the beginning.
                pollFailures = 0;
                showProgress("Checking the analysis...");
                poll();
            } else {
                jobId = null;
                jobFailed = false;
                loadPortfolioAndStart();
            }
        });
        askButton.setOnClickListener(v -> startActivity(ChatActivity.intent(this, jobId)));

        // A screen rotation keeps the same server job instead of starting a new one.
        jobId = savedInstanceState != null ? savedInstanceState.getString(STATE_JOB_ID) : null;
        if (jobId != null) {
            showProgress("Checking the analysis...");
            poll();
        } else {
            loadPortfolioAndStart();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_JOB_ID, jobId);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        pollHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void loadPortfolioAndStart() {
        showProgress("Loading your portfolio...");
        String userId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (userId == null) {
            showError("You're signed out. Please sign in again.");
            return;
        }
        getPortfolioUseCase.execute(userId, new ResultCallback<PortfolioSummary>() {
            @Override
            public void onSuccess(PortfolioSummary portfolio) {
                if (destroyed) return;
                showProgress("Sending your portfolio to the AI agents...");
                startAnalysisUseCase.execute(portfolio, new ResultCallback<String>() {
                    @Override
                    public void onSuccess(String newJobId) {
                        if (destroyed) return;
                        jobId = newJobId;
                        poll();
                    }

                    @Override
                    public void onError(String message) {
                        if (!destroyed) showError(message);
                    }
                });
            }

            @Override
            public void onError(String message) {
                if (!destroyed) showError(message);
            }
        });
    }

    private void poll() {
        getProgressUseCase.execute(jobId, new ResultCallback<AnalysisProgress>() {
            @Override
            public void onSuccess(AnalysisProgress progress) {
                if (destroyed) return;
                pollFailures = 0;
                switch (progress.getStatus()) {
                    case DONE:
                        showResult(progress.getAnalysis());
                        break;
                    case FAILED:
                        jobFailed = true;
                        showError(progress.getError());
                        break;
                    default:
                        showProgress(progressMessage(progress));
                        pollHandler.postDelayed(AnalysisActivity.this::poll, POLL_INTERVAL_MS);
                }
            }

            @Override
            public void onError(String message) {
                if (destroyed) return;
                pollFailures++;
                if (pollFailures < MAX_POLL_FAILURES) {
                    showProgress("Still waiting for the AI server (try " + (pollFailures + 1) + " of "
                            + MAX_POLL_FAILURES + ")...");
                    pollHandler.postDelayed(AnalysisActivity.this::poll, POLL_INTERVAL_MS);
                } else {
                    showError(message);
                }
            }
        });
    }

    private static String progressMessage(AnalysisProgress p) {
        if (p.getCurrentTicker() == null) {
            return p.getStocksDone() >= p.getStocksTotal() && p.getStocksTotal() > 0
                    ? "Writing your portfolio summary..."
                    : "Waiting to start...";
        }
        return String.format(Locale.US, "The AI agents are analysing %s (stock %d of %d)...",
                p.getCurrentTicker(), p.getStocksDone() + 1, p.getStocksTotal());
    }

    private void showProgress(String message) {
        progressSection.setVisibility(View.VISIBLE);
        resultSection.setVisibility(View.GONE);
        errorText.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
        statusText.setText(message);
    }

    private void showError(String message) {
        progressSection.setVisibility(View.GONE);
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
        retryButton.setVisibility(View.VISIBLE);
    }

    private void showResult(PortfolioAnalysis a) {
        progressSection.setVisibility(View.GONE);
        errorText.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
        resultSection.setVisibility(View.VISIBLE);

        ((TextView) findViewById(R.id.riskText)).setText(String.format(Locale.US,
                "Risk: %.0f%% (%s)", a.getRiskPercent(), a.getRiskLevel()));
        ((TextView) findViewById(R.id.numbersText)).setText(String.format(Locale.US,
                "Diversification score: %.0f / 100\nLargest position: %s (%.1f%% of portfolio)\nAnalysis date: %s",
                a.getDiversificationScore(), a.getLargestPosition(), a.getLargestPositionPercent(),
                a.getAnalysisDate()));
        ((TextView) findViewById(R.id.summaryText)).setText(a.getSummary());
        ((TextView) findViewById(R.id.strengthsText)).setText(bullets(a.getStrengths()));
        ((TextView) findViewById(R.id.weaknessesText)).setText(bullets(a.getWeaknesses()));
        ((TextView) findViewById(R.id.diversificationText)).setText(a.getDiversificationComment());

        LinearLayout stocks = findViewById(R.id.stocksContainer);
        stocks.removeAllViews();
        for (StockAnalysis s : a.getStocks()) {
            stocks.addView(text(s.getTicker() + ": " + s.getRating(), 16, true, 16));
            stocks.addView(text(preview(s.getDecision()), 14, false, 4));
        }
    }

    private TextView text(String value, int sizeSp, boolean bold, int marginTopDp) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(ContextCompat.getColor(this,
                bold ? R.color.piq_text_primary : R.color.piq_text_secondary));
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = Math.round(marginTopDp * getResources().getDisplayMetrics().density);
        view.setLayoutParams(params);
        return view;
    }

    private static String bullets(List<String> items) {
        if (items.isEmpty()) return "-";
        StringBuilder text = new StringBuilder();
        for (String item : items) {
            if (text.length() > 0) text.append('\n');
            text.append("• ").append(item);
        }
        return text.toString();
    }

    /** The agents write Markdown; show it as plain text, shortened. */
    private static String preview(String decision) {
        String plain = decision == null ? "" : decision.replaceAll("[*#`]", "").trim();
        return plain.length() > DECISION_PREVIEW_CHARS
                ? plain.substring(0, DECISION_PREVIEW_CHARS).trim() + "..."
                : plain;
    }
}
