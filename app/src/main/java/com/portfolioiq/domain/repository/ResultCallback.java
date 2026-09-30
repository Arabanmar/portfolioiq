package com.portfolioiq.domain.repository;

/**
 * Generic async result callback, same shape as AuthCallback but not tied to
 * authentication. Introduced in Sprint 2 for the portfolio/stock-price
 * repositories so those interfaces don't have to (mis)use AuthCallback.
 *
 * @param <T> the success result type
 */
public interface ResultCallback<T> {
    void onSuccess(T result);

    /**
     * @param message a message that is already safe to show the user —
     *                 repositories map raw exceptions to safe, generic text
     *                 before calling this.
     */
    void onError(String message);
}
