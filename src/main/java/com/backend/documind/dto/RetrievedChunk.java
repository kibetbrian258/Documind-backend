package com.backend.documind.dto;

import java.util.UUID;

/**
 * One passage found for a question, with where it came from.
 * Step 6 sends these to the AI model and turns them into citations.
 *
 * @param chunkId    the chunk's row id in vector_store
 * @param documentId the document it belongs to
 * @param fileName   the document's name, for showing the source
 * @param page       page number for PDFs; null for DOCX and TXT
 * @param chunkIndex position of the chunk in its document (0, 1, 2, ...)
 * @param text       the passage itself
 * @param similarity how close in meaning it is to the question, from 0 to 1
 */
public record RetrievedChunk(
        String chunkId,
        UUID documentId,
        String fileName,
        Integer page,
        int chunkIndex,
        String text,
        double similarity) {
}