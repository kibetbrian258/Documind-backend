package com.backend.documind.service;

import com.backend.documind.entity.Role;
import com.backend.documind.entity.User;
import com.backend.documind.repository.Userrepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static com.backend.documind.entity.Role.USER;

@Service
public class AuthService {
    private final Userrepository users;
    private final PasswordEncoder passwordEncoder;
    private final String dummyHash;


    public AuthService(Userrepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode("not-a-real-password-used-for-timing");
    }

    @Transactional
    public User register(String email, String rawPassword) {
        String normalisedEmail = normalise(email);
        if (users.existsByEmail(normalisedEmail)) {
            throw emailTaken();
        }

        User user = new User(normalisedEmail, passwordEncoder.encode(rawPassword), Role.USER, User.DEFAULT_DAILY_LIMIT);
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
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

    @Transactional(readOnly = true)
    public User getActiveUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Your session in no longer valid. Please log in again"
        ));
    }

    private static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static ResponseStatusException emailTaken() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
    }

    private static ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Incorrect email or password");
    }
}