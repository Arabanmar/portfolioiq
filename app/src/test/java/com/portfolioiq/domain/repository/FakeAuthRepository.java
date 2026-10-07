package com.portfolioiq.domain.repository;

import com.portfolioiq.domain.model.User;

/**
 * Hand-written test double for AuthRepository, used by RegisterUseCaseTest,
 * LoginUseCaseTest, and ForgotPasswordUseCaseTest. Firebase's own classes
 * can't be instantiated on a plain JVM unit test without instrumentation, so
 * use-case-level tests exercise validation and delegation logic against this
 * fake instead — it lets a test dictate exactly what the "repository"
 * returns (success or a specific error) and records whether
 * register()/login()/sendPasswordReset() was even called, which is what
 * proves the use case's own validation short-circuited correctly before
 * ever reaching the repository.
 */
public class FakeAuthRepository implements AuthRepository {

    public boolean registerCalled = false;
    public boolean loginCalled = false;
    public boolean sendPasswordResetCalled = false;
    public String lastUsername;
    public String lastEmail;
    public String lastPassword;

    /** Set before calling execute() to control what the fake returns. */
    public User successResult = new User("fake-uid", "someone@example.com");
    public String errorToReturn = null;

    @Override
    public void register(String username, String email, String password, AuthCallback<User> callback) {
        registerCalled = true;
        lastUsername = username;
        lastEmail = email;
        lastPassword = password;
        if (errorToReturn != null) {
            callback.onError(errorToReturn);
        } else {
            callback.onSuccess(successResult);
        }
    }

    @Override
    public void login(String email, String password, AuthCallback<User> callback) {
        loginCalled = true;
        lastEmail = email;
        lastPassword = password;
        if (errorToReturn != null) {
            callback.onError(errorToReturn);
        } else {
            callback.onSuccess(successResult);
        }
    }

    @Override
    public void sendPasswordReset(String email, AuthCallback<Void> callback) {
        sendPasswordResetCalled = true;
        lastEmail = email;
        if (errorToReturn != null) {
            callback.onError(errorToReturn);
        } else {
            callback.onSuccess(null);
        }
    }
}
