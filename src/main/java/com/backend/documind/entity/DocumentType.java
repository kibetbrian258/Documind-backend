package com.backend.documind.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The file types DocuMind accepts (FR-3), with the MIME type sent to browsers
 * and the extension used for the stored copy.
 */
@Getter
@RequiredArgsConstructor
public enum DocumentType {

    PDF("application/pdf", ".pdf"),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx"),
    TXT("text/plain", ".txt");

    private final String mimeType;
    private final String extension;

    /** The type for a stored content type, e.g. "application/pdf" gives PDF. */
    public static DocumentType fromMimeType(String mimeType) {
        for (DocumentType type : values()) {
            if (type.mimeType.equals(mimeType)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported content type: " + mimeType);
    }
}