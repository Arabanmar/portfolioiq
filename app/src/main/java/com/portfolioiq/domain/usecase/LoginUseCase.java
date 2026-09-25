package com.portfolioiq.domain.usecase;

import com.portfolioiq.domain.model.User;
import com.portfolioiq.domain.repository.AuthCallback;
import com.portfolioiq.domain.repository.AuthRepository;

/**
 * Login UseCase (execute() pattern — see RegisterUseCase for why). Deliberately
 * does NOT try to tell the caller whether the email or the password was wrong:
 * that distinction is a security bug (user enumeration), caught by ECC's
 * security-reviewer agent in Part One and covered by our own Sprint 1 test
 * case TC006. The generic-message rule is enforced in the repository, not
 * here, so it can never be bypassed by a future caller of this UseCase.
 */
public class LoginUseCase {

    private final AuthRepository authRepository;

    public LoginUseCase(AuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    public void execute(String email, String password, AuthCallback<User> callback) {
        if (email == null || email.trim().isEmpty()
                || password == null || password.isEmpty()) {
            callback.onError("Please enter both email and password.");
            return;
        }
        authRepository.login(email.trim(), password, callback);
    }
}
