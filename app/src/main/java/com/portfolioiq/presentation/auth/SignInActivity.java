package com.portfolioiq.presentation.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.portfolioiq.R;
import com.portfolioiq.data.repository.FirebaseAuthRepositoryImpl;
import com.portfolioiq.domain.model.User;
import com.portfolioiq.domain.repository.AuthCallback;
import com.portfolioiq.domain.repository.AuthRepository;
import com.portfolioiq.domain.usecase.LoginUseCase;

/**
 * Story 002 — Login & Authentication.
 *
 * This replaces the deliberately-flawed SignInActivity.java stub used for
 * the Part One live demo. Every issue security-reviewer found in that stub
 * is fixed here:
 *   - no hardcoded Firebase API key (see FirebaseAuthRepositoryImpl)
 *   - no logging of the raw password, anywhere
 *   - one generic error message for both "wrong password" and "no such
 *     account", per Sprint 1 test case TC006
 */
public class SignInActivity extends AppCompatActivity {

    private EditText emailInput;
    private EditText passwordInput;
    private Button signInButton;
    private TextView errorText;
    private TextView goToRegisterText;

    private LoginUseCase loginUseCase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);

        AuthRepository authRepository = new FirebaseAuthRepositoryImpl();
        loginUseCase = new LoginUseCase(authRepository);

        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        signInButton = findViewById(R.id.signInButton);
        errorText = findViewById(R.id.errorText);
        goToRegisterText = findViewById(R.id.goToRegisterText);

        signInButton.setOnClickListener(v -> attemptSignIn());
        goToRegisterText.setOnClickListener(v -> {
            startActivity(new Intent(SignInActivity.this, RegisterActivity.class));
            finish();
        });
    }

    private void attemptSignIn() {
        errorText.setVisibility(View.GONE);
        signInButton.setEnabled(false);

        String email = emailInput.getText().toString();
        String password = passwordInput.getText().toString();

        loginUseCase.execute(email, password, new AuthCallback<User>() {
            @Override
            public void onSuccess(User user) {
                signInButton.setEnabled(true);
                goToDashboard();
            }

            @Override
            public void onError(String message) {
                signInButton.setEnabled(true);
                errorText.setText(message);
                errorText.setVisibility(View.VISIBLE);
            }
        });
    }

    private void goToDashboard() {
        // Dashboard screen is Story 004 (Sprint 2) — not built yet.
        // TODO(Sprint 2): startActivity(new Intent(this, DashboardActivity.class));
        finish();
    }
}
