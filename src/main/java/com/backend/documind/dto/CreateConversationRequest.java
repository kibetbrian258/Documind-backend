package com.backend.documind.dto;

import jakarta.validation.constraints.Size;

/**
 * @param title optional; if left out, the first question becomes the title
 */
public record CreateConversationRequest(

        @Size(max = 200, message = "Title must be at most 200 characters.")
        String title) {
}