package com.backend.documind.entity;

/**
 * Where a document is in its life (FR-5).
 *
 * PROCESSING: uploaded; text is being extracted and embedded in the background.
 * READY:      searchable; questions can use it.
 * FAILED:     could not be processed; failureReason says why.
 */
public enum DocumentStatus {
    PROCESSING,
    READY,
    FAILED
}
