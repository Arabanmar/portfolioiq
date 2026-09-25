package com.portfolioiq.data.repository;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import com.portfolioiq.domain.model.User;
import com.portfolioiq.domain.repository.AuthCallback;
import com.portfolioiq.domain.repository.AuthRepository;

import java.util.HashMap;
import java.util.Map;

/**
 * Data-layer implementation of AuthRepository around FirebaseAuth + Firestore,
 * as decided in the project's architecture notes (Firebase AI Logic elsewhere
 * handles the LLM; this class only handles identity).
 *
 * Security notes (all three came directly out of the Part One live demo,
 * where ECC's security-reviewer agent found them in the throwaway demo stub —
 * this is the fixed, real Sprint 1 version):
 *  - No API key is hardcoded anywhere in this file. Firebase configuration
 *    comes from google-services.json, which is added to the Android Studio
 *    project per Firebase's own setup flow and is NOT committed with secrets
 *    exposed in source.
 *  - The password is never logged, not even at debug level.
 *  - login() maps BOTH "no such account" and "wrong password" to the exact
 *    same generic message, per Sprint 1 test case TC006. This is deliberate:
 *    telling an attacker which one it was is a user-enumeration bug.
 */
public class FirebaseAuthRepositoryImpl implements AuthRepository {

    private static final String USERS_COLLECTION = "users";
    private static final String GENERIC_LOGIN_ERROR = "Invalid email or password.";

    private final FirebaseAuth firebaseAuth;
    private final FirebaseFirestore firestore;

    public FirebaseAuthRepositoryImpl() {
        this.firebaseAuth = FirebaseAuth.getInstance();
        this.firestore = FirebaseFirestore.getInstance();
    }

    @Override
    public void register(String email, String password, AuthCallback<User> callback) {
        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser == null) {
                        callback.onError("Something went wrong. Please try again.");
                        return;
                    }
                    createUserProfile(firebaseUser, email);
                    callback.onSuccess(new User(firebaseUser.getUid(), email));
                })
                .addOnFailureListener(exception -> {
                    if (exception instanceof FirebaseAuthUserCollisionException) {
                        callback.onError("An account with this email already exists.");
                    } else if (exception instanceof FirebaseAuthWeakPasswordException) {
                        callback.onError("Choose a stronger password.");
                    } else {
                        callback.onError("Could not create the account. Please try again.");
                    }
                });
    }

    @Override
    public void login(String email, String password, AuthCallback<User> callback) {
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser == null) {
                        callback.onError(GENERIC_LOGIN_ERROR);
                        return;
                    }
                    callback.onSuccess(new User(firebaseUser.getUid(), email));
                })
                .addOnFailureListener(exception -> {
                    // TC006: wrong password and unregistered email both fall
                    // through to the SAME generic message. Do not special-case
                    // FirebaseAuthInvalidUserException vs
                    // FirebaseAuthInvalidCredentialsException here — that was
                    // exactly the bug the Part One demo deliberately contained.
                    if (exception instanceof FirebaseAuthInvalidUserException
                            || exception instanceof FirebaseAuthInvalidCredentialsException) {
                        callback.onError(GENERIC_LOGIN_ERROR);
                    } else {
                        callback.onError("Something went wrong. Please try again.");
                    }
                });
    }

    private void createUserProfile(@NonNull FirebaseUser firebaseUser, String email) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("email", email);
        profile.put("createdAt", System.currentTimeMillis());
        firestore.collection(USERS_COLLECTION)
                .document(firebaseUser.getUid())
                .set(profile);
        // Fire-and-forget by design for Sprint 1: auth succeeding is what
        // gates navigation. A failure writing the Firestore profile doc is
        // a follow-up concern for Sprint 2, not a reason to block sign-up.
    }
}
