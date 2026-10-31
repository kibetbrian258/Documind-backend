package com.backend.documind.dto;

import java.util.List;
import java.util.UUID;

/**
 * The three kinds of event sent while an answer streams (PRD section 10):
 *
 *   event: token      data: {"text":"The notice "}        many times, as the answer is written
 *   event: citations  data: {"messageId":..., "citations":[...]}   once, at the end
 *   event: error      data: {"message":"..."}             only if something went wrong
 */
public final class ChatEvents {

    public static final String TOKEN = "token";
    public static final String CITATIONS = "citations";
    public static final String ERROR = "error";

    private ChatEvents() {
    }

    /** A piece of the answer text. */
    public record Token(String text) {
    }

    /** Sent once the answer is complete; messageId identifies the saved answer (e.g. for feedback). */
    public record Citations(UUID messageId, List<CitationResponse> citations) {
    }

    /** A friendly message; the details are only in the server log. */
    public record StreamError(String message) {
    }
}