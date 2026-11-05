package com.backend.documind.service;

import com.backend.documind.entity.Role;
import com.backend.documind.entity.User;
import com.backend.documind.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Account rules: creating users and checking passwords.
 * Issuing the login cookie is the controller's job (see AuthController).
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    /**
     * A real BCrypt hash used when the email doesn't exist, so a failed login
     * takes the same time either way. Otherwise an attacker could tell which
     * emails have accounts by measuring response times.
     */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode("not-a-real-password-used-for-timing");
    }

    @Transactional
    public User register(String fullName, String email, String rawPassword) {
        String normalisedEmail = normalise(email);
        if (users.existsByEmail(normalisedEmail)) {
            throw emailTaken();
        }
        User user = new User(fullName.strip(), normalisedEmail, passwordEncoder.encode(rawPassword),
                Role.USER, User.DEFAULT_DAILY_LIMIT);
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Two sign-ups with the same email at the same moment: the unique constraint catches it.
            throw emailTaken();
        }
    }

    @Transactional(readOnly = true)
    public User authenticate(String email, String rawPassword) {
        Optional<User> user = users.findByEmail(normalise(email));
        if (user.isEmpty()) {
            passwordEncoder.matches(rawPassword, dummyHash);
            throw invalidCredentials();
        }
        if (!passwordEncoder.matches(rawPassword, user.get().getPasswordHash())) {
            throw invalidCredentials();
        }
        return user.get();
    }

    /** Loads the logged-in user; fails if the account was deleted after the token was issued. */
    @Transactional(readOnly = true)
    public User getActiveUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Your session is no longer valid. Please log in again."));
    }

    private static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static ResponseStatusException emailTaken() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
    }

    private static ResponseStatusException invalidCredentials() {
        // Same message for unknown email and wrong password, so it doesn't reveal which accounts exist.
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect email or password.");
    }
}