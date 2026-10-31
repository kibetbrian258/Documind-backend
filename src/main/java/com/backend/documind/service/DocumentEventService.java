package com.backend.documind.service;

import com.backend.documind.dto.DocumentResponse;
import com.backend.documind.entity.Document;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live document status updates over Server-Sent Events (US-3).
 *
 * The browser opens GET /api/v1/documents/events once and keeps it open. When
 * ingestion finishes a document, its new status is pushed down that connection,
 * so the UI updates without refreshing. Each user only receives their own events.
 */
@Slf4j
@Service
public class DocumentEventService {

    /** The browser reconnects automatically after this, so it can be fairly short. */
    private static final long CONNECTION_TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    private final Map<UUID, Set<SseEmitter>> connectionsByUser = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID userId) {
        SseEmitter emitter = new SseEmitter(CONNECTION_TIMEOUT_MS);
        connectionsByUser.computeIfAbsent(userId, id -> ConcurrentHashMap.newKeySet()).add(emitter);

        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onError(error -> remove(userId, emitter));
        emitter.onTimeout(() -> {
            remove(userId, emitter);
            emitter.complete();
        });

        try {
            // Sends the response headers straight away, so the browser knows it's connected.
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            remove(userId, emitter);
        }
        return emitter;
    }

    /** Sends a "document-status" event with the document's current details to its owner. */
    public void publishStatus(Document document) {
        Set<SseEmitter> connections = connectionsByUser.get(document.getUserId());
        if (connections == null) {
            return; // the owner has no page open
        }
        DocumentResponse payload = DocumentResponse.from(document);
        for (SseEmitter emitter : connections) {
            try {
                emitter.send(SseEmitter.event().name("document-status").data(payload));
            } catch (IOException | IllegalStateException e) {
                // The browser went away; forget this connection.
                remove(document.getUserId(), emitter);
            }
        }
    }

    private void remove(UUID userId, SseEmitter emitter) {
        connectionsByUser.computeIfPresent(userId, (id, connections) -> {
            connections.remove(emitter);
            return connections.isEmpty() ? null : connections;
        });
    }
}
