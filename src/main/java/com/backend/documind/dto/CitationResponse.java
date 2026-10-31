package com.backend.documind.dto;

import java.util.UUID;

/**
 * One source behind an answer, as shown under it (US-5).
 *
 * @param number     matches the [n] in the answer text
 * @param documentId use with GET /documents/{id}/file to open the document
 * @param page       the PDF page to open at; null for DOCX and TXT
 * @param snippet    the start of the passage that was used
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