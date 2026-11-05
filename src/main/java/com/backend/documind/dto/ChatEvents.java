package com.backend.documind.dto;

import java.util.List;
import java.util.UUID;

/**
 * The events sent while an answer streams (PRD section 10), in this order:
 *
 *   event: searched   data: {"passages":5,"documents":2}     once, when the search has finished
 *   event: token      data: {"text":"The notice "}            many times, as the answer is written
 *   event: citations  data: {"messageId":...,"notFound":false,"citations":[...]}   once, at the end
 *   event: error      data: {"message":"..."}                 only if something went wrong
 *
 * "searched" lets the screen show "Reading the 5 closest passages" while the
 * AI model is still getting ready to write. passages = 0 means the answer
 * will be the "not found" reply.
 */
public final class ChatEvents {

    public static final String SEARCHED = "searched";
    public static final String TOKEN = "token";
    public static final String CITATIONS = "citations";
    public static final String ERROR = "error";

    private ChatEvents() {
    }

    /**
     * @param passages  how many relevant passages were found (0 to top-K)
     * @param documents how many different documents those passages come from
     */
    public record Searched(int passages, int documents) {
    }

    /** A piece of the answer text. */
    public record Token(String text) {
    }

    /**
     * Sent once the answer is complete.
     *
     * @param messageId identifies the saved answer (e.g. for feedback)
     * @param notFound  true if the answer is the honest "I couldn't find that" reply (US-6)
     */
    public record Citations(UUID messageId, boolean notFound, List<CitationResponse> citations) {
    }

    /** A friendly message; the details are only in the server log. */
    public record StreamError(String message) {
    }
}