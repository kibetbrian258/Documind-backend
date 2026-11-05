package com.backend.documind.dto;

import com.backend.documind.entity.Role;

import java.time.Instant;
import java.util.UUID;

/**
 * A user as the admin sees them on the Users page.
 *
 * @param fullName       null for accounts created before names were added
 * @param questionsToday questions answered by the AI today (what counts towards the limit)
 * @param documents      how many documents they have in their Library
 */
public record AdminUserResponse(
        UUID id,
        String fullName,
        String email,
        Role role,
        int dailyLimit,
        long questionsToday,
        long documents,
        Instant createdAt) {
}