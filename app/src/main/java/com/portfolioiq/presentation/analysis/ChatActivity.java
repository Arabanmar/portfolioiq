package com.portfolioiq.presentation.analysis;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.portfolioiq.R;
import com.portfolioiq.data.repository.HttpAiAnalysisRepositoryImpl;
import com.portfolioiq.domain.repository.ResultCallback;
import com.portfolioiq.domain.usecase.AskPortfolioQuestionUseCase;

/**
 * Story 006: AI Chat Assistant. Answers questions about the portfolio the
 * user just analysed; the AI server answers only from that analysis' data.
 */
public class ChatActivity extends AppCompatActivity {

    private static final String EXTRA_JOB_ID = "job_id";

    private AskPortfolioQuestionUseCase askUseCase;
    private String jobId;
    private boolean destroyed = false;

    private LinearLayout messagesContainer;
    private ScrollView messagesScroll;
    private EditText questionInput;
    private Button sendButton;
    private TextView errorText;

    public static Intent intent(Context context, String jobId) {
        return new Intent(context, ChatActivity.class).putExtra(EXTRA_JOB_ID, jobId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        askUseCase = new AskPortfolioQuestionUseCase(new HttpAiAnalysisRepositoryImpl());
        jobId = getIntent().getStringExtra(EXTRA_JOB_ID);

        messagesContainer = findViewById(R.id.messagesContainer);
        messagesScroll = findViewById(R.id.messagesScroll);
        questionInput = findViewById(R.id.questionInput);
        sendButton = findViewById(R.id.sendButton);
        errorText = findViewById(R.id.errorText);

        addMessage("PortfolioIQ", "Ask me anything about your analysed portfolio, for example "
                + "\"Which stock is my biggest risk?\" or \"How can I diversify more?\"");
        sendButton.setOnClickListener(v -> send());
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        super.onDestroy();
    }

    private void send() {
        String question = questionInput.getText().toString();
        errorText.setVisibility(View.GONE);
        sendButton.setEnabled(false);
        askUseCase.execute(jobId, question, new ResultCallback<String>() {
            @Override
            public void onSuccess(String answer) {
                if (destroyed) return;
                sendButton.setEnabled(true);
                addMessage("You", question.trim());
                addMessage("PortfolioIQ", answer);
                questionInput.setText("");
            }

            @Override
            public void onError(String message) {
                if (destroyed) return;
                sendButton.setEnabled(true);
                errorText.setText(message);
                errorText.setVisibility(View.VISIBLE);
            }
        });
    }

    private void addMessage(String sender, String text) {
        TextView name = new TextView(this);
        name.setText(sender);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        name.setTextColor(ContextCompat.getColor(this, R.color.piq_accent_text));
        name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);

        TextView body = new TextView(this);
        body.setText(text == null ? "" : text.replaceAll("[*#`]", "").trim());
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        body.setTextColor(ContextCompat.getColor(this, R.color.piq_text_primary));
        body.setBackgroundColor(ContextCompat.getColor(this, R.color.piq_surface));
        int pad = Math.round(10 * getResources().getDisplayMetrics().density);
        body.setPadding(pad, pad, pad, pad);

        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = Math.round(12 * getResources().getDisplayMetrics().density);
        messagesContainer.addView(name, nameParams);
        messagesContainer.addView(body, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        messagesScroll.post(() -> messagesScroll.fullScroll(View.FOCUS_DOWN));
    }
}
