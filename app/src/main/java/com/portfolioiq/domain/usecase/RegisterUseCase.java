package com.portfolioiq.domain.usecase;

import com.portfolioiq.domain.model.User;
import com.portfolioiq.domain.repository.AuthCallback;
import com.portfolioiq.domain.repository.AuthRepository;
import com.portfolioiq.domain.util.EmailValidator;

import java.util.regex.Pattern;

/**
 * UseCase pattern adapted for plain Java (ECC's android-clean-architecture
 * skill is written for Kotlin/KMP's "operator fun invoke" — Java has no
 * equivalent, so every UseCase in this project instead exposes one
 * execute(...) method). One UseCase, one job: validate the input this
 * screen collected, then delegate to the repository.
 *
 * Email format is validated via EmailValidator (plain java.util.regex, no
 * android.util.Patterns dependency), so this class has zero Android
 * framework dependency and can be unit-tested on the plain JVM (no emulator
 * or instrumentation needed) — a clean-architecture domain layer should not
 * depend on the Android SDK.
 */
public class RegisterUseCase {

    /** 3-20 characters: letters, digits, underscore or dot (doctor's feedback, Sprint 3). */
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_.]{3,20}$");

    private final AuthRepository authRepository;

    public RegisterUseCase(AuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    public void execute(String username, String email, String password, String confirmPassword,
                         AuthCallback<User> callback) {
        if (username == null || username.trim().isEmpty()
                || email == null || email.trim().isEmpty()
                || password == null || password.isEmpty()
                || confirmPassword == null || confirmPassword.isEmpty()) {
            callback.onError("Please fill in every field.");
            return;
        }
        if (!USERNAME_PATTERN.matcher(username.trim()).matches()) {
            callback.onError("Username must be 3-20 characters: letters, numbers, _ or .");
            return;
        }
        if (!EmailValidator.isValid(email.trim())) {
            callback.onError("Enter a valid email address.");
            return;
        }
        if (password.length() < 6) {
            callback.onError("Password must be at least 6 characters.");
            return;
        }
        if (!password.equals(confirmPassword)) {
            callback.onError("Passwords do not match.");
            return;
        }
        authRepository.register(username.trim(), email.trim(), password, callback);
    }
}
