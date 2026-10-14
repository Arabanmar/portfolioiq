package com.portfolioiq.domain.repository;

/**
 * Domain-facing contract for reading a signed-in user's profile document.
 * The Firestore implementation lives in data/, same split as AuthRepository
 * and PortfolioRepository.
 */
public interface UserProfileRepository {

    /**
     * userId first: every call is scoped to one signed-in user. onSuccess
     * receives the stored username, or null if the profile or its username
     * field doesn't exist (that is not an error).
     */
    void getUsername(String userId, ResultCallback<String> callback);
}
