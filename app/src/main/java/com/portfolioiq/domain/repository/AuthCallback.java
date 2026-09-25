package com.portfolioiq.domain.repository;

/**
 * Generic async callback for auth operations. Firebase calls are async by
 * nature, so use cases and repositories communicate results this way
 * instead of throwing across layers.
 *
 * @param <T> the success result type
 */
public interface AuthCallback<T> {
    void onSuccess(T result);

    /**
     * @param message a message that is ALREADY safe to show the user —
     *                 repositories map raw exceptions to safe, generic text
     *                 before calling this (see FirebaseAuthRepositoryImpl).
     */
    void onError(String message);
}
