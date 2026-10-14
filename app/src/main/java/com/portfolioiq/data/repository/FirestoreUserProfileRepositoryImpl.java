package com.portfolioiq.data.repository;

import com.google.firebase.firestore.FirebaseFirestore;

import com.portfolioiq.domain.repository.ResultCallback;
import com.portfolioiq.domain.repository.UserProfileRepository;

/**
 * Data-layer implementation of UserProfileRepository around Firestore. Reads
 * the profile document FirebaseAuthRepositoryImpl writes at registration:
 * users/{userId}, field "username".
 */
public class FirestoreUserProfileRepositoryImpl implements UserProfileRepository {

    private static final String USERS_COLLECTION = "users";
    private static final String FIELD_USERNAME = "username";
    private static final String GENERIC_ERROR = "Could not load your profile. Please try again.";

    private final FirebaseFirestore firestore;

    public FirestoreUserProfileRepositoryImpl() {
        this.firestore = FirebaseFirestore.getInstance();
    }

    @Override
    public void getUsername(String userId, ResultCallback<String> callback) {
        firestore.collection(USERS_COLLECTION).document(userId).get()
                .addOnSuccessListener(snapshot -> {
                    // getString() returns null when the document or the field
                    // is missing (or isn't a string), which the use case
                    // turns into "Welcome back" rather than an error.
                    callback.onSuccess(snapshot.exists() ? snapshot.getString(FIELD_USERNAME) : null);
                })
                .addOnFailureListener(exception -> callback.onError(GENERIC_ERROR));
    }
}
