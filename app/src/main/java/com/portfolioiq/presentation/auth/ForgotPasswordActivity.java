package com.portfolioiq.presentation.auth;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.portfolioiq.R;
import com.portfolioiq.data.repository.FirebaseAuthRepositoryImpl;
import com.portfolioiq.domain.repository.AuthCallback;
import com.portfolioiq.domain.repository.AuthRepository;
import com.portfolioiq.domain.usecase.ForgotPasswordUseCase;

/**
 * Forgot Password screen, reached from Sign In. Presentation layer only —
 * same pattern as RegisterActivity/SignInActivity: no FirebaseAuth calls
 * happen here directly, all of that lives behind AuthRepository /
 * ForgotPasswordUseCase.
 *
 * The success message is shown for both a registered and an unregistered
 * email (FirebaseAuthRepositoryImpl.sendPasswordReset maps both to
 * onSuccess) — this screen never reveals whether an address has an account,
 * matching the app's existing TC006 generic-message policy for login.
 */
public class ForgotPasswordActivity extends AppCompatActivity {

    private EditText emailInput;
    private Button sendResetButton;
    private TextView errorText;
    private TextView successText;
    private TextView backToSignInText;

    private ForgotPasswordUseCase forgotPasswordUseCase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        AuthRepository authRepository = new FirebaseAuthRepositoryImpl();
        forgotPasswordUseCase = new ForgotPasswordUseCase(authRepository);

        emailInput = findViewById(R.id.emailInput);
        sendResetButton = findViewById(R.id.sendResetButton);
        errorText = findViewById(R.id.errorText);
        successText = findViewById(R.id.successText);
        backToSignInText = findViewById(R.id.backToSignInText);

        sendResetButton.setOnClickListener(v -> attemptSendReset());
        backToSignInText.setOnClickListener(v -> finish());
    }

    private void attemptSendReset() {
        errorText.setVisibility(View.GONE);
        successText.setVisibility(View.GONE);
        sendResetButton.setEnabled(false);

        String email = emailInput.getText().toString();

        forgotPasswordUseCase.execute(email, new AuthCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                sendResetButton.setEnabled(true);
                successText.setText(
                        "If an account exists for that email, a reset link is on its way. Check your inbox.");
                successText.setVisibility(View.VISIBLE);
            }

            @Override
            public void onError(String message) {
                sendResetButton.setEnabled(true);
                errorText.setText(message);
                errorText.setVisibility(View.VISIBLE);
            }
        });
    }
}
