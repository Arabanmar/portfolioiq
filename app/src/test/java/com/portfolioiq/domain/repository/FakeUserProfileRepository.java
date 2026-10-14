package com.portfolioiq.domain.repository;

/**
 * Hand-written test double for UserProfileRepository (Firestore can't be
 * instantiated in a plain JVM unit test). Records the call and lets a test
 * dictate the outcome via usernameToReturn / errorToReturn.
 */
public class FakeUserProfileRepository implements UserProfileRepository {

    public boolean getUsernameCalled = false;
    public String lastUserId;

    public String usernameToReturn = null;
    public String errorToReturn = null;

    @Override
    public void getUsername(String userId, ResultCallback<String> callback) {
        getUsernameCalled = true;
        lastUserId = userId;
        if (errorToReturn != null) {
            callback.onError(errorToReturn);
        } else {
            callback.onSuccess(usernameToReturn);
        }
    }
}
