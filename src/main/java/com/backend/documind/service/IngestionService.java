package com.backend.documind.service;

import com.backend.documind.config.DocumentProperties;
import com.backend.documind.entity.Document;
import com.backend.documind.entity.DocumentStatus;
import com.backend.documind.entity.DocumentType;
import com.backend.documind.exception.DocumentProcessingException;
import com.backend.documind.repository.DocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

/**
 * Makes an uploaded document searchable (FR-4, FR-5, FR-6):
 *
 *   read the file -> extract text -> cut into chunks -> embed -> store in vector_store
 *
 * then marks the document READY, or FAILED with a reason the user can read.
 *
 * Runs in the background (see DocumentIngestionListener). Embedding can take a
 * while, so it happens outside any database transaction; only the short status
 * updates are transactional.
 */
@Slf4j
@Service
public class IngestionService {

    private final DocumentRepository documents;
    private final StorageService storage;
    private final DocumentTextExtractor textExtractor;
    private final TextChunker chunker;
    private final VectorStore vectorStore;
    private final DocumentEventService events;
    private final TransactionTemplate transactionTemplate;

    /** Limits how many documents are processed at once, so a burst of uploads can't overload the AI model. */
    private final Semaphore processingSlots;

    public IngestionService(DocumentRepository documents,
                            StorageService storage,
                            DocumentTextExtractor textExtractor,
                            TextChunker chunker,
                            VectorStore vectorStore,
                            DocumentEventService events,
                            TransactionTemplate transactionTemplate,
                            DocumentProperties properties) {
        this.documents = documents;
        this.storage = storage;
        this.textExtractor = textExtractor;
        this.chunker = chunker;
        this.vectorStore = vectorStore;
        this.events = events;
        this.transactionTemplate = transactionTemplate;
        this.processingSlots = new Semaphore(properties.maxConcurrentIngestions());
    }

    public void ingest(UUID documentId) {
        processingSlots.acquireUninterruptibly();
        try {
            process(documentId);
        } finally {
            processingSlots.release();
        }
    }

    /** Ids of documents still waiting to be processed, e.g. after a restart. */
    public List<UUID> findUnfinished() {
        return documents.findAllByStatus(DocumentStatus.PROCESSING).stream().map(Document::getId).toList();
    }

    /** Removes every chunk of a document from the vector store. Joins the caller's transaction if there is one. */
    public void deleteChunks(UUID documentId) {
        vectorStore.delete(new FilterExpressionBuilder()
                .eq(TextChunker.DOCUMENT_ID, documentId.toString())
                .build());
    }

    private void process(UUID documentId) {
        Document document = documents.findById(documentId).orElse(null);
        if (document == null || document.getStatus() != DocumentStatus.PROCESSING) {
            return; // deleted, or already handled
        }
        long startedAt = System.currentTimeMillis();
        try {
            DocumentType type = DocumentType.fromMimeType(document.getContentType());
            var text = textExtractor.extract(type, storage.load(document.getStoragePath()));
            var chunks = chunker.chunk(document.getUserId(), documentId, text.pages());
            if (chunks.isEmpty()) {
                throw new DocumentProcessingException("No text was found in this document.");
            }

            deleteChunks(documentId); // in case an earlier attempt stopped half-way
            vectorStore.add(chunks);  // calls the embedding model, then inserts the rows

            Document updated = updateStatus(documentId, d -> d.markReady(text.pageCount()));
            if (updated == null) {
                // The user deleted the document while it was being processed.
                deleteChunks(documentId);
                return;
            }
            log.info("Document {} ready: {} chunks in {} ms",
                    documentId, chunks.size(), System.currentTimeMillis() - startedAt);

        } catch (DocumentProcessingException e) {
            log.info("Document {} failed: {}", documentId, e.getMessage());
            fail(documentId, e.getMessage());
        } catch (RuntimeException e) {
            log.error("Document {} could not be processed", documentId, e);
            fail(documentId, "Something went wrong while processing this document. Please try again later.");
        }
    }

    private void fail(UUID documentId, String reason) {
        try {
            deleteChunks(documentId);
        } catch (RuntimeException e) {
            log.warn("Could not clean up chunks of failed document {}", documentId, e);
        }
        updateStatus(documentId, d -> d.markFailed(reason));
    }

    /**
     * Applies a status change in its own short transaction, then tells the
     * owner's open pages. Returns null if the document no longer exists.
     */
    private Document updateStatus(UUID documentId, Consumer<Document> change) {
        Document updated = transactionTemplate.execute(status ->
                documents.findById(documentId)
                        .map(document -> {
                            change.accept(document);
                            return document;
                        })
                        .orElse(null));
        if (updated != null) {
            events.publishStatus(updated);
        }
        return updated;
    }
}
