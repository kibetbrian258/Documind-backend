package com.backend.documind.dto;

import com.backend.documind.entity.Role;
import com.backend.documind.entity.User;

import java.time.Instant;
import java.util.UUID;

/**
 * What the API returns about a user. Never includes the password hash.
 */
public record UserResponse(
        UUID id,
        String email,
        Role role,
        int dailyLimit,
        int questionsRemainingToday,
        Instant createdAt) {

    /** @param questionsRemainingToday from QuotaService.remainingToday */
    public static UserResponse from(User user, int questionsRemainingToday) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getDailyLimit(),
                questionsRemainingToday,
                user.getCreatedAt());
    }
}