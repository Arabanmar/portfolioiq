package com.portfolioiq.domain.usecase;

import com.portfolioiq.domain.repository.ResultCallback;
import com.portfolioiq.domain.repository.UserProfileRepository;

/**
 * Dashboard greeting. Loads the signed-in user's username from their profile
 * and hands back the finished greeting line, so the Activity only displays it
 * (CLAUDE.md: no business rules in Activities). A missing or blank username
 * is not an error: the greeting just falls back to "Welcome back".
 */
public class GetUserProfileUseCase {

    static final String SIGNED_OUT_ERROR = "You're signed out. Please sign in again.";
    static final String GREETING_WITHOUT_NAME = "Welcome back";

    private final UserProfileRepository userProfileRepository;

    public GetUserProfileUseCase(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    public void execute(String userId, ResultCallback<String> callback) {
        if (userId == null || userId.trim().isEmpty()) {
            callback.onError(SIGNED_OUT_ERROR);
            return;
        }

        userProfileRepository.getUsername(userId.trim(), new ResultCallback<String>() {
            @Override
            public void onSuccess(String username) {
                if (username == null || username.trim().isEmpty()) {
                    callback.onSuccess(GREETING_WITHOUT_NAME);
                    return;
                }
                callback.onSuccess("Welcome, " + username.trim());
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }
}
