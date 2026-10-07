package com.portfolioiq.domain.model;

/**
 * Domain model for an authenticated PortfolioIQ user.
 * Plain data holder — no Firebase types leak into the domain layer.
 */
public class User {

    private final String uid;
    private final String email;
    /** Chosen at sign-up. May be null where it isn't known, e.g. right after login. */
    private final String username;

    public User(String uid, String email) {
        this(uid, email, null);
    }

    public User(String uid, String email, String username) {
        this.uid = uid;
        this.email = email;
        this.username = username;
    }

    public String getUid() {
        return uid;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }
}
