package com.backend.documind.service;

import com.backend.documind.config.ChatProperties;
import com.backend.documind.dto.AskRequest;
import com.backend.documind.dto.ChatEvents;
import com.backend.documind.dto.CitationResponse;
import com.backend.documind.dto.RetrievedChunk;
import com.backend.documind.entity.ChatMessage;
import com.backend.documind.entity.Conversation;
import com.backend.documind.entity.MessageCitation;
import com.backend.documind.entity.MessageRole;
import com.backend.documind.repository.ChatMessageRepository;
import com.backend.documind.repository.ConversationRepository;
import com.backend.documind.repository.MessageCitationRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A question in a conversation, answered live (US-4, FR-8 to FR-10, FR-14).
 *
 * RagAnswerService produces the answer; this class adds what a conversation needs:
 * - checks the conversation belongs to the user
 * - saves the question, the answer and its citations
 * - sends a "searched" event, each piece of the answer as a "token" event, then a "citations" event
 * - removes the question again if no answer could be given (error, or the user pressed Stop)
 * - records tokens, cost and timing
 *
 * The work runs on a virtual thread so the request thread is free while the model writes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int MAX_PASSAGE_LENGTH = 4_000;
    private static final String GENERIC_ERROR =
            "The answer service didn't respond. We tried twice. This didn't use up a question — try again in a moment.";

    private final RagAnswerService ragAnswerService;
    private final ConversationService conversationService;
    private final QuotaService quotaService;
    private final ConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final MessageCitationRepository citations;
    private final UsageService usageService;
    private final TransactionTemplate transactionTemplate;
    private final ChatProperties properties;
    private final ExecutorService streamExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Checks access and the daily limit straight away (so problems come back as a
     * normal 404 or 429), then streams the answer in the background.
     */
    public SseEmitter ask(UUID userId, UUID conversationId, AskRequest request) {
        Conversation conversation = conversationService.getOwned(userId, conversationId);
        quotaService.checkCanAsk(userId);
        long receivedAt = System.currentTimeMillis();
        SseEmitter emitter = new SseEmitter(properties.streamTimeout().toMillis());
        streamExecutor.execute(() -> answer(userId, conversation.getId(), request, emitter, receivedAt));
        return emitter;
    }

    @PreDestroy
    void shutdown() {
        streamExecutor.shutdown();
    }

    private void answer(UUID userId, UUID conversationId, AskRequest request, SseEmitter emitter, long receivedAt) {
        String question = request.question().strip();
        UUID questionId = null;
        try {
            List<ChatMessage> history = recentMessages(conversationId); // read before adding this question
            questionId = saveQuestion(conversationId, question);

            RagAnswerService.Answer answer = ragAnswerService.answer(
                    userId, question, history, request.documentIds(),
                    passages -> send(emitter, ChatEvents.SEARCHED, new ChatEvents.Searched(passages.size(),
                            (int) passages.stream().map(RetrievedChunk::documentId).distinct().count())),
                    piece -> send(emitter, ChatEvents.TOKEN, new ChatEvents.Token(piece)));

            UUID messageId = saveAnswer(conversationId, answer);
            questionId = null; // answered and saved: keep the question whatever happens next
            if (answer.modelCalled()) {
                usageService.recordChat(userId, messageId, answer.model(),
                        answer.inputTokens(), answer.outputTokens(),
                        answer.firstTokenAt() - receivedAt, answer.finishedAt() - receivedAt);
            }

            send(emitter, ChatEvents.CITATIONS, new ChatEvents.Citations(messageId, answer.notFound(),
                    answer.cited().stream()
                            .map(p -> new CitationResponse(p.number(), p.chunk().documentId(), p.chunk().fileName(),
                                    p.chunk().page(), passage(p.chunk().text()), p.chunk().similarity()))
                            .toList()));
            emitter.complete();

        } catch (ClientGoneException e) {
            // "Stop" was pressed or the tab was closed. Nothing was answered, so the question is removed
            // too; otherwise the conversation would show a question with no answer.
            log.debug("Client left conversation {} before the answer finished", conversationId);
            removeQuestion(questionId);
        } catch (RuntimeException e) {
            log.error("Answering failed in conversation {}", conversationId, e);
            removeQuestion(questionId); // so "Try again" doesn't leave the question in the conversation twice
            sendQuietly(emitter, ChatEvents.ERROR, new ChatEvents.StreamError(GENERIC_ERROR));
            emitter.complete();
        }
    }

    /** The last N messages (FR-10), oldest first. */
    private List<ChatMessage> recentMessages(UUID conversationId) {
        List<ChatMessage> newestFirst = new ArrayList<>(messages.findByConversationIdOrderByCreatedAtDesc(
                conversationId, PageRequest.of(0, properties.memoryMessages())));
        Collections.reverse(newestFirst);
        return newestFirst;
    }

    private UUID saveQuestion(UUID conversationId, String question) {
        return transactionTemplate.execute(status -> {
            conversations.findById(conversationId).ifPresent(conversation -> {
                conversation.titleFromFirstQuestion(question);
                conversation.touch();
            });
            return messages.save(new ChatMessage(conversationId, MessageRole.USER, question)).getId();
        });
    }

    private void removeQuestion(UUID questionId) {
        if (questionId == null) {
            return;
        }
        try {
            messages.deleteById(questionId);
        } catch (RuntimeException e) {
            log.warn("Couldn't remove unanswered question {}", questionId, e);
        }
    }

    private UUID saveAnswer(UUID conversationId, RagAnswerService.Answer answer) {
        return transactionTemplate.execute(status -> {
            ChatMessage saved = messages.save(new ChatMessage(conversationId, MessageRole.ASSISTANT, answer.text()));
            citations.saveAll(answer.cited().stream()
                    .map(p -> new MessageCitation(saved.getId(), p.chunk().documentId(), p.number(),
                            p.chunk().page(), UUID.fromString(p.chunk().chunkId()),
                            p.chunk().similarity(), passage(p.chunk().text())))
                    .toList());
            conversations.findById(conversationId).ifPresent(Conversation::touch);
            return saved.getId();
        });
    }

    /**
     * The passage as shown in the source panel: the whole chunk (about 500 tokens),
     * with runs of spaces and line breaks tidied up. The cap is only a safety limit.
     */
    private static String passage(String text) {
        String clean = text.strip().replaceAll("\\s+", " ");
        return clean.length() <= MAX_PASSAGE_LENGTH ? clean : clean.substring(0, MAX_PASSAGE_LENGTH).strip() + "…";
    }

    private static void send(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException | IllegalStateException e) {
            throw new ClientGoneException();
        }
    }

    private static void sendQuietly(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException | IllegalStateException ignored) {
            // the browser is already gone
        }
    }

    /** The browser closed the connection; stop quietly (this also stops the model). */
    private static class ClientGoneException extends RuntimeException {
        ClientGoneException() {
            super(null, null, false, false);
        }
    }
}