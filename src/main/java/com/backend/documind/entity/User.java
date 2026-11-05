package com.backend.documind.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A DocuMind account. Maps to the "users" table created by V2__create_users.sql.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // required by JPA; not for application code
public class User {

    /** Questions per day for new accounts (FR-15 starting value). */
    public static final int DEFAULT_DAILY_LIMIT = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    /** Null for accounts created before names were added (V7). */
    @Column(name = "full_name", length = 100)
    private String fullName;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "daily_limit", nullable = false)
    private int dailyLimit;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public User(String fullName, String email, String passwordHash, Role role, int dailyLimit) {
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.dailyLimit = dailyLimit;
        this.createdAt = Instant.now();
    }

    /** An admin changes how many questions this user may ask per day (FR-15). */
    public void changeDailyLimit(int newLimit) {
        if (newLimit < 0) {
            throw new IllegalArgumentException("The daily limit can't be negative.");
        }
        this.dailyLimit = newLimit;
    }
}