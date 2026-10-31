package com.backend.documind.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The admin dashboard (US-11, FR-16): usage, cost, errors and users for the last N days.
 *
 * @param days     how many days are covered, today included
 * @param timeZone the time zone days are counted in (same as the daily question limit)
 * @param from     start of the period
 * @param totals   the whole period added up
 * @param byDay    one row per user per day with activity, newest day first
 * @param overview users, document health and answer ratings
 */
public record AdminUsageResponse(
        int days,
        String timeZone,
        Instant from,
        Totals totals,
        List<UserDay> byDay,
        Overview overview) {

    /**
     * @param questions             AI-answered questions (includes evaluation runs)
     * @param avgCostPerQuestionUsd PRD target: under $0.01
     * @param medianFirstTokenMs    PRD target: 2000 ms or less
     * @param activeUsers           users who asked at least one question
     */
    public record Totals(
            long questions,
            long inputTokens,
            long outputTokens,
            BigDecimal estimatedCostUsd,
            BigDecimal avgCostPerQuestionUsd,
            Long medianFirstTokenMs,
            long activeUsers) {
    }

    /** "Which users used the most tokens this week?" (PRD, admin persona) */
    public record UserDay(
            LocalDate day,
            UUID userId,
            String email,
            long questions,
            long inputTokens,
            long outputTokens,
            BigDecimal estimatedCostUsd,
            Long avgFirstTokenMs) {
    }

    /**
     * @param documentsFailed     current number of documents that couldn't be processed (errors)
     * @param thumbsUp            ratings given during the period
     * @param satisfactionPercent thumbs up / all ratings; PRD target: 80% or more
     */
    public record Overview(
            long totalUsers,
            long newUsers,
            long documentsReady,
            long documentsProcessing,
            long documentsFailed,
            long thumbsUp,
            long thumbsDown,
            Double satisfactionPercent) {
    }
}