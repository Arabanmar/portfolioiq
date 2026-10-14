package com.portfolioiq.domain.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.portfolioiq.domain.repository.FakeUserProfileRepository;
import com.portfolioiq.domain.repository.ResultCallback;

import org.junit.Before;
import org.junit.Test;

public class GetUserProfileUseCaseTest {

    private FakeUserProfileRepository fakeRepository;
    private GetUserProfileUseCase useCase;

    @Before
    public void setUp() {
        fakeRepository = new FakeUserProfileRepository();
        useCase = new GetUserProfileUseCase(fakeRepository);
    }

    @Test
    public void nullUserId_showsSignedOutErrorWithoutCallingRepository() {
        Result result = execute(null);

        assertEquals("You're signed out. Please sign in again.", result.error);
        assertFalse(fakeRepository.getUsernameCalled);
    }

    @Test
    public void blankUserId_showsSignedOutErrorWithoutCallingRepository() {
        Result result = execute("   ");

        assertEquals("You're signed out. Please sign in again.", result.error);
        assertFalse(fakeRepository.getUsernameCalled);
    }

    @Test
    public void usernameFound_returnsWelcomeWithName() {
        fakeRepository.usernameToReturn = "demo_user";

        Result result = execute("user-1");

        assertTrue(fakeRepository.getUsernameCalled);
        assertEquals("user-1", fakeRepository.lastUserId);
        assertEquals("Welcome, demo_user", result.greeting);
        assertNull(result.error);
    }

    @Test
    public void usernameWithSurroundingSpaces_isTrimmed() {
        fakeRepository.usernameToReturn = "  demo_user  ";

        Result result = execute("user-1");

        assertEquals("Welcome, demo_user", result.greeting);
    }

    @Test
    public void userIdWithSurroundingSpaces_isTrimmedBeforeDelegating() {
        fakeRepository.usernameToReturn = "demo_user";

        execute("  user-1  ");

        assertEquals("user-1", fakeRepository.lastUserId);
    }

    @Test
    public void noUsername_returnsWelcomeBack() {
        fakeRepository.usernameToReturn = null;

        Result result = execute("user-1");

        assertEquals("Welcome back", result.greeting);
        assertNull(result.error);
    }

    @Test
    public void blankUsername_returnsWelcomeBack() {
        fakeRepository.usernameToReturn = "   ";

        Result result = execute("user-1");

        assertEquals("Welcome back", result.greeting);
        assertNull(result.error);
    }

    @Test
    public void repositoryError_propagatesToCallback() {
        fakeRepository.errorToReturn = "Could not load your profile. Please try again.";

        Result result = execute("user-1");

        assertEquals("Could not load your profile. Please try again.", result.error);
        assertNull(result.greeting);
    }

    @Test
    public void emptyStringUserId_showsSignedOutErrorWithoutCallingRepository() {
        Result result = execute("");

        assertEquals("You're signed out. Please sign in again.", result.error);
        assertEquals(1, result.errorCount);
        assertEquals(0, result.successCount);
        assertNull(result.greeting);
        assertFalse(fakeRepository.getUsernameCalled);
        assertNull(fakeRepository.lastUserId);
    }

    @Test
    public void tabAndNewlineOnlyUserId_showsSignedOutErrorWithoutCallingRepository() {
        Result result = execute(" \t\n ");

        assertEquals("You're signed out. Please sign in again.", result.error);
        assertEquals(0, result.successCount);
        assertFalse(fakeRepository.getUsernameCalled);
    }

    @Test
    public void signedOutUserId_neverCallsOnSuccess() {
        fakeRepository.usernameToReturn = "demo_user";

        Result result = execute(null);

        assertEquals(0, result.successCount);
        assertNull(result.greeting);
        assertFalse(fakeRepository.getUsernameCalled);
    }

    @Test
    public void emptyStringUsername_returnsWelcomeBack() {
        fakeRepository.usernameToReturn = "";

        Result result = execute("user-1");

        assertTrue(fakeRepository.getUsernameCalled);
        assertEquals("Welcome back", result.greeting);
        assertNull(result.error);
    }

    @Test
    public void tabAndNewlineOnlyUsername_returnsWelcomeBack() {
        fakeRepository.usernameToReturn = "\t\n  \r\n";

        Result result = execute("user-1");

        assertTrue(fakeRepository.getUsernameCalled);
        assertEquals("Welcome back", result.greeting);
        assertNull(result.error);
    }

    @Test
    public void usernameWithTabsAndNewlinesAround_isTrimmed() {
        fakeRepository.usernameToReturn = "\t demo_user\n";

        Result result = execute("user-1");

        assertEquals("Welcome, demo_user", result.greeting);
        assertNull(result.error);
    }

    @Test
    public void usernameWithInternalSpaces_keepsThemAndOnlyTrimsEnds() {
        fakeRepository.usernameToReturn = "  Anmar  Arab ";

        Result result = execute("user-1");

        assertEquals("Welcome, Anmar  Arab", result.greeting);
        assertNull(result.error);
    }

    @Test
    public void successfulLookup_callsOnSuccessExactlyOnceAndNeverOnError() {
        fakeRepository.usernameToReturn = "demo_user";

        Result result = execute("user-1");

        assertEquals(1, result.successCount);
        assertEquals(0, result.errorCount);
        assertNull(result.error);
    }

    @Test
    public void welcomeBackFallback_callsOnSuccessExactlyOnceAndNeverOnError() {
        fakeRepository.usernameToReturn = null;

        Result result = execute("user-1");

        assertEquals(1, result.successCount);
        assertEquals(0, result.errorCount);
    }

    @Test
    public void repositoryError_reachesRepositoryAndNeverCallsOnSuccess() {
        fakeRepository.errorToReturn = "Could not load your profile. Please try again.";
        fakeRepository.usernameToReturn = "demo_user";

        Result result = execute("  user-1 ");

        assertTrue(fakeRepository.getUsernameCalled);
        assertEquals("user-1", fakeRepository.lastUserId);
        assertEquals("Could not load your profile. Please try again.", result.error);
        assertEquals(1, result.errorCount);
        assertEquals(0, result.successCount);
        assertNull(result.greeting);
    }

    private Result execute(String userId) {
        Result result = new Result();
        useCase.execute(userId, new ResultCallback<String>() {
            @Override
            public void onSuccess(String greeting) {
                result.successCount++;
                result.greeting = greeting;
            }

            @Override
            public void onError(String message) {
                result.errorCount++;
                result.error = message;
            }
        });
        return result;
    }

    private static class Result {
        String greeting;
        String error;
        int successCount = 0;
        int errorCount = 0;
    }
}
