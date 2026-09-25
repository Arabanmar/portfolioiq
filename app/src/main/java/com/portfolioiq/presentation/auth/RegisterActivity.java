package com.portfolioiq.presentation.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.portfolioiq.R;
import com.portfolioiq.data.repository.FirebaseAuthRepositoryImpl;
import com.portfolioiq.domain.model.User;
import com.portfolioiq.domain.repository.AuthCallback;
import com.portfolioiq.domain.repository.AuthRepository;
import com.portfolioiq.domain.usecase.RegisterUseCase;

/**
 * Story 001 — Registration.
 * Presentation layer only: no FirebaseAuth calls happen here directly, all
 * of that lives behind AuthRepository / RegisterUseCase so this class can be
 * unit-tested with a fake repository later.
 */
public class RegisterActivity extends AppCompatActivity {

    private EditText emailInput;
    private EditText passwordInput;
    private EditText confirmPasswordInput;
    private Button registerButton;
    private TextView errorText;
    private TextView goToSignInText;

    private RegisterUseCase registerUseCase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        AuthRepository authRepository = new FirebaseAuthRepositoryImpl();
        registerUseCase = new RegisterUseCase(authRepository);

        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);
        registerButton = findViewById(R.id.registerButton);
        errorText = findViewById(R.id.errorText);
        goToSignInText = findViewById(R.id.goToSignInText);

        registerButton.setOnClickListener(v -> attemptRegister());
        goToSignInText.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, SignInActivity.class));
            finish();
        });
    }

    private void attemptRegister() {
        errorText.setVisibility(View.GONE);
        registerButton.setEnabled(false);

        String email = emailInput.getText().toString();
        String password = passwordInput.getText().toString();
        String confirmPassword = confirmPasswordInput.getText().toString();

        registerUseCase.execute(email, password, confirmPassword, new AuthCallback<User>() {
            @Override
            public void onSuccess(User user) {
                registerButton.setEnabled(true);
                Toast.makeText(RegisterActivity.this,
                        "Account created. Please sign in.", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(RegisterActivity.this, SignInActivity.class));
                finish();
            }

            @Override
            public void onError(String message) {
                registerButton.setEnabled(true);
                errorText.setText(message);
                errorText.setVisibility(View.VISIBLE);
            }
        });
    }
}
