package com.backend.documind.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
public class User {

    // Questions per day for new accounts
    public static final int DEFAULT_DAILY_LIMIT = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false, length = 50)
    private String email;

    @Column(nullable = false, length = 254, name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false, name = "daily_limit")
    private int dailyLimit;

    @Column(nullable = false, name = "created_at", updatable = false)
    private Instant createdAt;


    public User (String email, String passwordHash, Role role, int dailyLimit) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.dailyLimit = dailyLimit;
        this.createdAt = Instant.now();
    }
}
