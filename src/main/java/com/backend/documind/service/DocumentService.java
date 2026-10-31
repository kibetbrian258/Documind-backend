package com.backend.documind.service;

import com.backend.documind.config.DocumentProperties;
import com.backend.documind.entity.Document;
import com.backend.documind.entity.DocumentStatus;
import com.backend.documind.entity.DocumentType;
import com.backend.documind.events.DocumentUploadedEvent;
import com.backend.documind.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * Upload, list, download and delete documents.
 *
 * Privacy rule: every method takes the logged-in user's id and only ever
 * touches that user's documents. Someone else's document id gives "not found",
 * exactly as if it didn't exist, so ids can't be probed.
 *
 * The database row and the file on disk are kept in step: if saving the row
 * fails the file is removed, and a file is only deleted once the row's
 * deletion has been committed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final int MAX_FILE_NAME_LENGTH = 255;

    private final DocumentRepository documents;
    private final StorageService storage;
    private final FileTypeDetector fileTypeDetector;
    private final DocumentProperties properties;
    private final IngestionService ingestionService;
    private final ApplicationEventPublisher eventPublisher;

    /** The document plus its file contents, for download. */
    public record DocumentFile(Document document, Resource content) {
    }

    @Transactional
    public Document upload(UUID userId, MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file is empty.");
        }
        if (documents.countByUserId(userId) >= properties.maxPerUser()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You've reached the limit of " + properties.maxPerUser()
                            + " documents. Delete one to upload another.");
        }
        DocumentType type = fileTypeDetector.detect(file).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only PDF, DOCX and TXT files are supported."));

        // Stored under a random name: the user's file name never touches the file system.
        String storageKey = userId + "/" + UUID.randomUUID() + type.getExtension();
        try (InputStream content = file.getInputStream()) {
            storage.store(storageKey, content);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The upload could not be read. Please try again.");
        }
        // If anything below fails, the transaction rolls back and the stored file is removed.
        runAfterRollback(() -> storage.delete(storageKey));

        Document document = new Document(
                userId, cleanFileName(file.getOriginalFilename()), type.getMimeType(), file.getSize(), storageKey);
        Document saved = documents.save(document);
        log.info("User {} uploaded document {} ({}, {} bytes)", userId, saved.getId(), type, file.getSize());
        // Processing starts in the background once this transaction commits (DocumentIngestionListener).
        eventPublisher.publishEvent(new DocumentUploadedEvent(saved.getId()));
        return saved;
    }

    /**
     * Processes a FAILED document again, e.g. after the AI service was down
     * (Reliability NFR). A scanned PDF will simply fail again with the same reason.
     */
    @Transactional
    public Document retry(UUID userId, UUID documentId) {
        Document document = get(userId, documentId);
        if (document.getStatus() != DocumentStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only documents that failed can be retried.");
        }
        document.markProcessing();
        // Same path as a new upload: processing starts once this transaction commits.
        eventPublisher.publishEvent(new DocumentUploadedEvent(documentId));
        log.info("User {} retried document {}", userId, documentId);
        return document;
    }

    @Transactional(readOnly = true)
    public List<Document> list(UUID userId) {
        return documents.findAllByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public Document get(UUID userId, UUID documentId) {
        return documents.findByIdAndUserId(documentId, userId).orElseThrow(DocumentService::notFound);
    }

    @Transactional(readOnly = true)
    public DocumentFile loadFile(UUID userId, UUID documentId) {
        Document document = get(userId, documentId);
        return new DocumentFile(document, storage.load(document.getStoragePath()));
    }

    @Transactional
    public void delete(UUID userId, UUID documentId) {
        Document document = get(userId, documentId);
        documents.delete(document);
        // Same transaction: if anything fails, neither the row nor the chunks are deleted.
        // Citations of this document are removed by the database (ON DELETE CASCADE).
        ingestionService.deleteChunks(documentId);
        runAfterCommit(() -> {
            try {
                storage.delete(document.getStoragePath());
            } catch (RuntimeException e) {
                // The record is already gone, so the user sees success; the leftover file is only logged.
                log.warn("Document {} deleted but its file could not be removed", document.getId(), e);
            }
        });
        log.info("User {} deleted document {}", userId, documentId);
    }

    /** Keeps only the file's own name (no folders) and removes control characters. */
    static String cleanFileName(String originalName) {
        if (originalName == null) {
            return "document";
        }
        int lastSeparator = Math.max(originalName.lastIndexOf('/'), originalName.lastIndexOf('\\'));
        String name = originalName.substring(lastSeparator + 1).replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty()) {
            return "document";
        }
        return name.length() > MAX_FILE_NAME_LENGTH ? name.substring(0, MAX_FILE_NAME_LENGTH) : name;
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found.");
    }

    private static void runAfterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private static void runAfterRollback(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    action.run();
                }
            }
        });
    }
}