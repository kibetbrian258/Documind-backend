package com.backend.documind.service;

import com.backend.documind.config.ChatProperties;
import com.backend.documind.entity.User;
import com.backend.documind.repository.UsageRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * The daily question limit (FR-15), which keeps AI costs predictable.
 *
 * What counts: every question that reached the AI model today, taken from
 * usage_records. Those rows stay even if the user deletes a conversation, so
 * deleting chats can't reset the limit. "Not found" replies skip the model,
 * cost nothing, and don't count.
 *
 * "Today" starts at midnight in documind.chat.daily-limit-time-zone.
 * Each user's limit is users.daily_limit (50 by default).
 */
@Service
@RequiredArgsConstructor
public class QuotaService {

    private final UsageRecordRepository usageRecords;
    private final AuthService authService;
    private final ChatProperties properties;

    /**
     * @param remaining questions left today
     * @param resetsAt  when the count starts again from zero (the next midnight)
     */
    public record Quota(int remaining, Instant resetsAt) {
    }

    @Transactional(readOnly = true)
    public Quota today(User user) {
        return new Quota(remainingToday(user), startOfTomorrow());
    }

    @Transactional(readOnly = true)
    public int remainingToday(User user) {
        long used = usageRecords.countByUserIdAndCreatedAtGreaterThanEqual(user.getId(), startOfToday());
        return (int) Math.max(0, user.getDailyLimit() - used);
    }

    /** Throws 429 Too Many Requests with a friendly message once today's questions are used up. */
    @Transactional(readOnly = true)
    public void checkCanAsk(UUID userId) {
        User user = authService.getActiveUser(userId);
        if (remainingToday(user) <= 0) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "You've used all " + user.getDailyLimit() + " questions for today. "
                            + "Your limit resets at midnight (" + zone().getId() + ").");
        }
    }

    private Instant startOfToday() {
        ZoneId zone = zone();
        return LocalDate.now(zone).atStartOfDay(zone).toInstant();
    }

    private Instant startOfTomorrow() {
        ZoneId zone = zone();
        return LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant();
    }

    private ZoneId zone() {
        return ZoneId.of(properties.dailyLimitTimeZone());
    }
}