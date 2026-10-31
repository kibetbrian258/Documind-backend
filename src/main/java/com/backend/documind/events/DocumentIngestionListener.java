package com.backend.documind.events;

import com.backend.documind.service.IngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.UUID;

/**
 * Starts ingestion in the background (FR-5), so the upload request can return
 * 202 straight away instead of making the user wait.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentIngestionListener {

    private final IngestionService ingestionService;

    /**
     * Runs only after the upload's database transaction has committed, so the
     * document row is guaranteed to exist when processing starts.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentUploaded(DocumentUploadedEvent event) {
        ingestionService.ingest(event.documentId());
    }

    /** Picks up documents left in PROCESSING when the app last stopped. */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void resumeUnfinished() {
        List<UUID> unfinished = ingestionService.findUnfinished();
        if (!unfinished.isEmpty()) {
            log.info("Resuming processing of {} document(s)", unfinished.size());
            unfinished.forEach(ingestionService::ingest);
        }
    }
}
