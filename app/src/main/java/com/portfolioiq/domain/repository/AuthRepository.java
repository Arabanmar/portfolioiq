package com.portfolioiq.domain.repository;

import com.portfolioiq.domain.model.User;

/**
 * Domain-facing contract for authentication. The implementation (Firebase,
 * or anything else later) lives in the data/ package — the domain and
 * presentation layers never import FirebaseAuth directly.
 */
public interface AuthRepository {

    /** username is already validated and trimmed by RegisterUseCase. */
    void register(String username, String email, String password, AuthCallback<User> callback);

    void login(String email, String password, AuthCallback<User> callback);

    /**
     * Sends a password-reset email for the given address. There is no
     * meaningful "result" beyond success/failure, so the callback is typed
     * Void — onSuccess(null) means the reset email was sent.
     */
    void sendPasswordReset(String email, AuthCallback<Void> callback);
}
