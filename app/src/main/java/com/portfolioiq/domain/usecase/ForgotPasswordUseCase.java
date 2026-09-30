package com.portfolioiq.domain.usecase;

import com.portfolioiq.domain.repository.AuthCallback;
import com.portfolioiq.domain.repository.AuthRepository;
import com.portfolioiq.domain.util.EmailValidator;

/**
 * Forgot Password UseCase (execute() pattern — see RegisterUseCase). Only
 * validates that an email was entered and is well-formed, then delegates to
 * AuthRepository.sendPasswordReset. The repository is responsible for the
 * generic-message-regardless-of-outcome behavior that prevents this screen
 * from being used to check whether an email address has an account
 * (the same user-enumeration concern as LoginUseCase's TC006) — this
 * UseCase intentionally has no opinion on that, it only validates input.
 */
public class ForgotPasswordUseCase {

    private final AuthRepository authRepository;

    public ForgotPasswordUseCase(AuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    public void execute(String email, AuthCallback<Void> callback) {
        if (email == null || email.trim().isEmpty()) {
            callback.onError("Please enter your email.");
            return;
        }
        if (!EmailValidator.isValid(email.trim())) {
            callback.onError("Enter a valid email address.");
            return;
        }
        authRepository.sendPasswordReset(email.trim(), callback);
    }
}
