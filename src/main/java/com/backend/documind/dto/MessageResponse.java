package com.backend.documind.dto;

import com.backend.documind.entity.FeedbackRating;
import com.backend.documind.entity.MessageRole;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A message in a conversation; assistant messages carry their citations and,
 * if the user rated the answer, their feedback (UP, DOWN or null).
 *
 * @param notFound true for an answer that is the honest "I couldn't find that" reply (US-6),
 *                 so the screen can show it the same way as when it was first answered
 */
public record MessageResponse(
        UUID id,
        MessageRole role,
        String content,
        Instant createdAt,
        List<CitationResponse> citations,
        FeedbackRating feedback,
        boolean notFound) {
}