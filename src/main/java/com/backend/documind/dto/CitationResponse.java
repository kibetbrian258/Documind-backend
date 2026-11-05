package com.backend.documind.dto;

import java.util.UUID;

/**
 * One source behind an answer, as shown under it (US-5).
 *
 * @param number     matches the [n] in the answer text
 * @param documentId use with GET /documents/{id}/file to open the document
 * @param page       the PDF page to open at; null for DOCX and TXT
 * @param snippet    the passage that was used (the whole passage, for the source panel;
 *                   answers saved before this change have only its first 300 characters)
 * @param similarity how relevant the passage was to the question, from 0 to 1
 */
public record CitationResponse(
        int number,
        UUID documentId,
        String fileName,
        Integer page,
        String snippet,
        double similarity) {
}