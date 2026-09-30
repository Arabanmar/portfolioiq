package com.portfolioiq.domain.util;

import java.util.regex.Pattern;

/**
 * Shared email-format check for the domain layer. Plain java.util.regex, not
 * android.util.Patterns, so every use case that needs it stays instantiable
 * on a plain JVM unit test (see RegisterUseCase's Javadoc for why that
 * matters). Extracted out of RegisterUseCase so RegisterUseCase and
 * ForgotPasswordUseCase can't silently drift into two different definitions
 * of "valid email" over time.
 */
public final class EmailValidator {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private EmailValidator() {
    }

    public static boolean isValid(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }
}
