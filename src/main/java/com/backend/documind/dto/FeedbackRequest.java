package com.backend.documind.dto;

import com.backend.documind.entity.FeedbackRating;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param rating  UP or DOWN
 * @param comment optional: what was good or wrong about the answer
 */
public record FeedbackRequest(

        @NotNull(message = "Rating is required: UP or DOWN.")
        FeedbackRating rating,

        @Size(max = 1000, message = "Comment must be at most 1000 characters.")
        String comment) {
}