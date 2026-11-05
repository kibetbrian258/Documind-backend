package com.backend.documind.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * @param dailyLimit questions per day; 0 pauses asking for this user
 */
public record UpdateDailyLimitRequest(

        @NotNull(message = "Daily limit is required.")
        @Min(value = 0, message = "Daily limit can't be negative.")
        @Max(value = 1000, message = "Daily limit can be at most 1000.")
        Integer dailyLimit) {
}