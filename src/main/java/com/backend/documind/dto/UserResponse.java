package com.backend.documind.dto;

import com.backend.documind.entity.Role;
import com.backend.documind.entity.User;
import com.backend.documind.service.QuotaService;

import java.time.Instant;
import java.util.UUID;

/**
 * What the API returns about a user. Never includes the password hash.
 *
 * @param questionsResetAt when today's questions refill (the next midnight in
 *                         documind.chat.daily-limit-time-zone), for "refills in 5 h 12 min"
 */
public record UserResponse(
        UUID id,
        String fullName,
        String email,
        Role role,
        int dailyLimit,
        int questionsRemainingToday,
        Instant questionsResetAt,
        Instant createdAt) {

    /** @param quota from QuotaService.today */
    public static UserResponse from(User user, QuotaService.Quota quota) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getDailyLimit(),
                quota.remaining(),
                quota.resetsAt(),
                user.getCreatedAt());
    }
}