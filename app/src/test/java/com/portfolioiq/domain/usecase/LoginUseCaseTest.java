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
 * Plain JVM unit tests for LoginUseCase.
 *
 * These map onto PortfolioIQ_Sprint1_TestCases.docx's Login table
 * (TC001-TC005) after that doc was trimmed to match the app as actually
 * shipped: LoginUseCase does not validate email format at all (only
 * blank-field checks), so the doc no longer claims it does. TC002
 * (password masked with dots while typing) is a UI case, not covered
 * here.
 */
public class LoginUseCaseTest {

    private FakeAuthRepository fakeRepository;
    private LoginUseCase useCase;
    private CapturingCallback callback;

    @Before
    public void setUp() {
        fakeRepository = new FakeAuthRepository();
        useCase = new LoginUseCase(fakeRepository);
        callback = new CapturingCallback();
    }

    @Test
    public void bothFieldsBlank_showsEnterBothError() {
        // Maps to Sprint 1 TestCases TC001.
        useCase.execute("", "", callback);

        assertEquals("Please enter both email and password.", callback.errorMessage);
        assertFalse("repository should not be called when validation fails", fakeRepository.loginCalled);
    }

    @Test
    public void blankEmail_showsEnterBothError() {
        // Maps to Sprint 1 TestCases TC001.
        useCase.execute("", "password1", callback);

        assertEquals("Please enter both email and password.", callback.errorMessage);
        assertFalse(fakeRepository.loginCalled);
    }

    @Test
    public void blankPassword_showsEnterBothError() {
        // Maps to Sprint 1 TestCases TC001 — the doc uses one combined
        // message for "Email, Password, or both blank", which matches this
        // use case's actual (single, non-field-specific) validation.
        useCase.execute("user@example.com", "", callback);

        assertEquals("Please enter both email and password.", callback.errorMessage);
        assertFalse(fakeRepository.loginCalled);
    }

    @Test
    public void validInput_delegatesToRepositoryWithTrimmedEmail() {
        useCase.execute("  user@example.com  ", "password1", callback);

        assertTrue("repository should be called for valid input", fakeRepository.loginCalled);
        assertEquals("email should be trimmed before reaching the repository",
                "user@example.com", fakeRepository.lastEmail);
        assertEquals("password1", fakeRepository.lastPassword);
    }

    @Test
    public void repositorySuccess_propagatesUserToCallback() {
        // Maps to Sprint 1 TestCases TC005 (valid sign-in succeeds; the doc
        // notes the app currently returns to Welcome, not a Dashboard,
        // since that screen is Sprint 2 work — this use-case-level test
        // only checks that the User is propagated, not which screen shows).
        User expected = new User("uid-123", "user@example.com");
        fakeRepository.successResult = expected;

        useCase.execute("user@example.com", "password1", callback);

        assertNotNull(callback.successUser);
        assertEquals(expected.getUid(), callback.successUser.getUid());
        assertEquals(expected.getEmail(), callback.successUser.getEmail());
        assertNull(callback.errorMessage);
    }

    @Test
    public void repositoryError_propagatesGenericMessageToCallback() {
        // Maps to Sprint 1 TestCases TC003/TC004 (wrong password AND
        // unregistered email both use the exact same generic message —
        // this is FirebaseAuthRepositoryImpl's responsibility, not the use
        // case's; this test only proves the use case passes it through
        // unmodified, whatever it is).
        fakeRepository.errorToReturn = "Invalid email or password.";

        useCase.execute("user@example.com", "wrongpassword", callback);

        assertEquals("Invalid email or password.", callback.errorMessage);
        assertNull(callback.successUser);
    }

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
