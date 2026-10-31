package com.backend.documind.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * @param question    the user's question
 * @param documentIds optional: only use these documents (FR-11); empty or missing means all of mine
 */
public record AskRequest(

        @NotBlank(message = "Question is required.")
        @Size(max = 2000, message = "Question must be at most 2000 characters.")
        String question,

        @Size(max = 50, message = "Select at most 50 documents.")
        List<UUID> documentIds) {
}