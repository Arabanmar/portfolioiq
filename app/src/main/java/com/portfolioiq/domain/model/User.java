package com.portfolioiq.domain.model;

/**
 * Domain model for an authenticated PortfolioIQ user.
 * Plain data holder — no Firebase types leak into the domain layer.
 */
public class User {

    private final String uid;
    private final String email;

    public User(String uid, String email) {
        this.uid = uid;
        this.email = email;
    }

    public String getUid() {
        return uid;
    }

    public String getEmail() {
        return email;
    }
}
