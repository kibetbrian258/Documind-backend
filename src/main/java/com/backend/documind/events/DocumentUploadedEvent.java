package com.backend.documind.events;

import java.util.UUID;

/**
 * Published by DocumentService when a file has been uploaded.
 * DocumentIngestionListener reacts to it by starting background processing.
 */
public record DocumentUploadedEvent(UUID documentId) {
}
