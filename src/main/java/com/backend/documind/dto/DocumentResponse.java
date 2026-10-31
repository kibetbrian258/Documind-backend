package com.backend.documind.dto;

import com.backend.documind.entity.Document;
import com.backend.documind.entity.DocumentStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * What the API returns about a document. The storage path is left out on purpose.
 */
public record DocumentResponse(
        UUID id,
        String fileName,
        String contentType,
        long sizeBytes,
        Integer pageCount,
        DocumentStatus status,
        String failureReason,
        Instant createdAt) {

    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getFileName(),
                document.getContentType(),
                document.getSizeBytes(),
                document.getPageCount(),
                document.getStatus(),
                document.getFailureReason(),
                document.getCreatedAt());
    }
}