package com.backend.documind.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * An uploaded file and its processing status. Maps to the "documents" table.
 *
 * The owner is stored as a plain userId rather than a link to the User entity:
 * every query filters on it, and nothing here needs the rest of the user.
 */
@Entity
@Table(name = "documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // required by JPA; not for application code
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** Filled in by ingestion (step 4); null until then. */
    @Column(name = "page_count")
    private Integer pageCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    /** Where the file is kept, relative to the storage folder. Never shown to users. */
    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Document(UUID userId, String fileName, String contentType, long sizeBytes, String storagePath) {
        this.userId = userId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storagePath = storagePath;
        this.status = DocumentStatus.PROCESSING;
        this.createdAt = Instant.now();
    }

    /** Called by ingestion when the document is searchable. pageCount is null for DOCX and TXT. */
    public void markReady(Integer pageCount) {
        this.status = DocumentStatus.READY;
        this.pageCount = pageCount;
        this.failureReason = null;
    }

    /** Puts a failed document back in the queue (the user pressed "retry"). */
    public void markProcessing() {
        this.status = DocumentStatus.PROCESSING;
        this.failureReason = null;
    }

    /** Called by ingestion when processing fails; the reason is shown to the user. */
    public void markFailed(String reason) {
        this.status = DocumentStatus.FAILED;
        this.failureReason = reason;
    }
}