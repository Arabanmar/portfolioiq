package com.portfolioiq.domain.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.portfolioiq.domain.model.User;
import com.portfolioiq.domain.repository.AuthCallback;
import com.portfolioiq.domain.repository.FakeAuthRepository;

import org.junit.Before;
import org.junit.Test;

/**
 * Plain JVM unit tests for RegisterUseCase — no emulator/instrumentation
 * needed, runs via `Run Tests` in Android Studio or `./gradlew test`.
 *
 * These map 1:1 onto PortfolioIQ_Sprint1_TestCases.docx's Registration
 * table (TC001-TC007) after that doc was trimmed to match the app as
 * actually shipped (no "Full Name" field; blank email/password/confirm
 * all collapse into one combined message). TC001 (tapping "Create
 * Account" navigates to Sign Up) is a UI-navigation case and isn't
 * covered here — everything else is.
 */
public class RegisterUseCaseTest {

    private FakeAuthRepository fakeRepository;
    private RegisterUseCase useCase;
    private CapturingCallback callback;

    @Before
    public void setUp() {
        fakeRepository = new FakeAuthRepository();
        useCase = new RegisterUseCase(fakeRepository);
        callback = new CapturingCallback();
    }

    @Test
    public void blankEmail_showsFillInEveryFieldError() {
        // Maps to Sprint 1 TestCases TC002.
        useCase.execute("", "password1", "password1", callback);

        assertEquals("Please fill in every field.", callback.errorMessage);
        assertFalse("repository should not be called when validation fails", fakeRepository.registerCalled);
    }

    @Test
    public void blankPassword_showsFillInEveryFieldError() {
        // Maps to Sprint 1 TestCases TC002.
        useCase.execute("user@example.com", "", "", callback);

        assertEquals("Please fill in every field.", callback.errorMessage);
        assertFalse(fakeRepository.registerCalled);
    }

    @Test
    public void blankConfirmPassword_showsFillInEveryFieldError() {
        // Maps to Sprint 1 TestCases TC002.
        useCase.execute("user@example.com", "password1", "", callback);

        assertEquals("Please fill in every field.", callback.errorMessage);
        assertFalse(fakeRepository.registerCalled);
    }

    @Test
    public void invalidEmailFormat_showsValidEmailError() {
        // Maps to Sprint 1 TestCases TC003. Wording matches the doc exactly:
        // "Enter a valid email address."
        useCase.execute("not-an-email", "password1", "password1", callback);

        assertEquals("Enter a valid email address.", callback.errorMessage);
        assertFalse(fakeRepository.registerCalled);
    }

    @Test
    public void passwordTooShort_showsMinLengthError() {
        // Maps to Sprint 1 TestCases TC004.
        useCase.execute("user@example.com", "abc", "abc", callback);

        assertEquals("Password must be at least 6 characters.", callback.errorMessage);
        assertFalse(fakeRepository.registerCalled);
    }

    @Test
    public void passwordMismatch_showsPasswordsDoNotMatchError() {
        // Maps to Sprint 1 TestCases TC005.
        useCase.execute("user@example.com", "password1", "password2", callback);

        assertEquals("Passwords do not match.", callback.errorMessage);
        assertFalse(fakeRepository.registerCalled);
    }

    @Test
    public void validInput_delegatesToRepositoryWithTrimmedEmail() {
        useCase.execute("  user@example.com  ", "password1", "password1", callback);

        assertTrue("repository should be called for valid input", fakeRepository.registerCalled);
        assertEquals("email should be trimmed before reaching the repository",
                "user@example.com", fakeRepository.lastEmail);
        assertEquals("password1", fakeRepository.lastPassword);
    }

    @Test
    public void repositorySuccess_propagatesUserToCallback() {
        // Maps to Sprint 1 TestCases TC007 (valid registration succeeds).
        User expected = new User("uid-123", "user@example.com");
        fakeRepository.successResult = expected;

        useCase.execute("user@example.com", "password1", "password1", callback);

        assertNotNull(callback.successUser);
        assertEquals(expected.getUid(), callback.successUser.getUid());
        assertEquals(expected.getEmail(), callback.successUser.getEmail());
        assertNull(callback.errorMessage);
    }

    @Test
    public void repositoryError_propagatesMessageToCallback() {
        // Maps to Sprint 1 TestCases TC006 (email already registered) — the
        // exact message ("An account with this email already exists.") is
        // produced by FirebaseAuthRepositoryImpl, not RegisterUseCase, so
        // this test only proves the use case passes the repository's error
        // straight through unmodified.
        fakeRepository.errorToReturn = "An account with this email already exists.";

        useCase.execute("user@example.com", "password1", "password1", callback);

        assertEquals("An account with this email already exists.", callback.errorMessage);
        assertNull(callback.successUser);
    }

    /** Simple synchronous capture of whichever AuthCallback method fires. */
    private static class CapturingCallback implements AuthCallback<User> {
        User successUser;
        String errorMessage;

        @Override
        public void onSuccess(User result) {
            successUser = result;
        }

        @Override
        public void onError(String message) {
            errorMessage = message;
        }
    }
}
