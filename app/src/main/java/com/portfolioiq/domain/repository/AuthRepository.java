package com.portfolioiq.domain.repository;

import com.portfolioiq.domain.model.User;

/**
 * Domain-facing contract for authentication. The implementation (Firebase,
 * or anything else later) lives in the data/ package — the domain and
 * presentation layers never import FirebaseAuth directly.
 */
public interface AuthRepository {

    void register(String email, String password, AuthCallback<User> callback);

    void login(String email, String password, AuthCallback<User> callback);
}
