package com.backend.documind.exception;

/**
 * A document could not be made searchable for a reason the user can act on,
 * such as a scanned or password-protected PDF.
 *
 * The message is shown to the user as the document's failure reason, so it
 * must be friendly and must not contain technical details.
 */
public class DocumentProcessingException extends RuntimeException {

    public DocumentProcessingException(String userFacingReason) {
        super(userFacingReason);
    }
}
