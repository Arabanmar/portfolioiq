package com.portfolioiq.domain.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.portfolioiq.domain.repository.AuthCallback;
import com.portfolioiq.domain.repository.FakeAuthRepository;

import org.junit.Before;
import org.junit.Test;

/**
 * Plain JVM unit tests for ForgotPasswordUseCase — no emulator needed.
 *
 * These test only the use case's own input validation and delegation. The
 * "never reveal whether an email has an account" behavior lives in
 * FirebaseAuthRepositoryImpl (which maps FirebaseAuthInvalidUserException to
 * a success callback), not here, so it isn't retested against the fake —
 * the fake always succeeds or errors exactly as told, same as the other
 * UseCase tests in this package.
 */
public class ForgotPasswordUseCaseTest {

    private FakeAuthRepository fakeRepository;
    private ForgotPasswordUseCase useCase;
    private CapturingCallback callback;

    @Before
    public void setUp() {
        fakeRepository = new FakeAuthRepository();
        useCase = new ForgotPasswordUseCase(fakeRepository);
        callback = new CapturingCallback();
    }

    @Test
    public void blankEmail_showsEnterEmailError() {
        useCase.execute("", callback);

        assertEquals("Please enter your email.", callback.errorMessage);
        assertFalse("repository should not be called when validation fails", fakeRepository.sendPasswordResetCalled);
    }

    @Test
    public void whitespaceOnlyEmail_showsEnterEmailError() {
        useCase.execute("   ", callback);

        assertEquals("Please enter your email.", callback.errorMessage);
        assertFalse(fakeRepository.sendPasswordResetCalled);
    }

    @Test
    public void invalidEmailFormat_showsValidEmailError() {
        useCase.execute("not-an-email", callback);

        assertEquals("Enter a valid email address.", callback.errorMessage);
        assertFalse(fakeRepository.sendPasswordResetCalled);
    }

    @Test
    public void validEmail_delegatesToRepositoryWithTrimmedEmail() {
        useCase.execute("  user@example.com  ", callback);

        assertTrue("repository should be called for valid input", fakeRepository.sendPasswordResetCalled);
        assertEquals("email should be trimmed before reaching the repository",
                "user@example.com", fakeRepository.lastEmail);
    }

    @Test
    public void repositorySuccess_propagatesToCallback() {
        useCase.execute("user@example.com", callback);

        assertTrue(callback.successCalled);
        assertNull(callback.errorMessage);
    }

    @Test
    public void repositoryError_propagatesMessageToCallback() {
        // Maps to a genuine failure (e.g. network), not an unregistered
        // email — FirebaseAuthRepositoryImpl treats "no such account" as
        // success, not an error, so this only tests pass-through of
        // whatever error the repository does decide to raise.
        fakeRepository.errorToReturn = "Could not send the reset email. Please try again.";

        useCase.execute("user@example.com", callback);

        assertEquals("Could not send the reset email. Please try again.", callback.errorMessage);
        assertFalse(callback.successCalled);
    }

    /** Simple synchronous capture of whichever AuthCallback method fires. */
    private static class CapturingCallback implements AuthCallback<Void> {
        boolean successCalled = false;
        String errorMessage;

        @Override
        public void onSuccess(Void result) {
            successCalled = true;
        }

        @Override
        public void onError(String message) {
            errorMessage = message;
        }
    }
}
