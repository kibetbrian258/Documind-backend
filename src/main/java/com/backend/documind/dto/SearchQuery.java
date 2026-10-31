package com.backend.documind.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Body of the development-only search endpoint.
 *
 * @param question      what to search for
 * @param documentIds   optional: only search these documents (empty or missing means all of mine)
 * @param minSimilarity optional: override the similarity threshold, e.g. 0 to see every score while tuning
 */
public record SearchQuery(

        @NotBlank(message = "Question is required.")
        @Size(max = 2000, message = "Question must be at most 2000 characters.")
        String question,

        @Size(max = 50, message = "Select at most 50 documents.")
        List<UUID> documentIds,

        @DecimalMin(value = "0.0", message = "minSimilarity must be between 0 and 1.")
        @DecimalMax(value = "1.0", message = "minSimilarity must be between 0 and 1.")
        Double minSimilarity) {
}