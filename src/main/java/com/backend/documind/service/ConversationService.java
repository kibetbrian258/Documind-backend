package com.backend.documind.service;

import com.backend.documind.dto.CitationResponse;
import com.backend.documind.dto.MessageResponse;
import com.backend.documind.entity.ChatMessage;
import com.backend.documind.entity.Conversation;
import com.backend.documind.entity.Document;
import com.backend.documind.entity.FeedbackRating;
import com.backend.documind.entity.MessageFeedback;
import com.backend.documind.entity.MessageCitation;
import com.backend.documind.entity.MessageRole;
import com.backend.documind.repository.ChatMessageRepository;
import com.backend.documind.repository.ConversationRepository;
import com.backend.documind.repository.DocumentRepository;
import com.backend.documind.repository.MessageCitationRepository;
import com.backend.documind.repository.MessageFeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Creating and reading conversations (US-9).
 *
 * Privacy rule, as for documents: every method takes the current user's id,
 * and someone else's conversation gives "not found".
 */
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final MessageCitationRepository citations;
    private final DocumentRepository documents;
    private final MessageFeedbackRepository feedback;
    private final RagAnswerService ragAnswerService;

    @Transactional
    public Conversation create(UUID userId, String title) {
        String cleanTitle = title == null || title.isBlank() ? null : title.strip();
        return conversations.save(new Conversation(userId, cleanTitle));
    }

    @Transactional(readOnly = true)
    public List<Conversation> list(UUID userId) {
        return conversations.findAllByUserIdOrderByUpdatedAtDesc(userId);
    }

    /** US-9: rename. */
    @Transactional
    public Conversation rename(UUID userId, UUID conversationId, String title) {
        Conversation conversation = getOwned(userId, conversationId);
        conversation.rename(title);
        return conversation;
    }

    /**
     * US-9: delete. The database removes its messages and their citations too
     * (ON DELETE CASCADE). Usage records stay, so the daily limit and cost
     * totals are unaffected.
     */
    @Transactional
    public void delete(UUID userId, UUID conversationId) {
        conversations.delete(getOwned(userId, conversationId));
    }

    @Transactional(readOnly = true)
    public Conversation getOwned(UUID userId, UUID conversationId) {
        return conversations.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found."));
    }

    /** The whole conversation, oldest first, with each answer's citations. */
    @Transactional(readOnly = true)
    public List<MessageResponse> messages(UUID userId, UUID conversationId) {
        getOwned(userId, conversationId);

        List<ChatMessage> conversationMessages = messages.findAllByConversationIdOrderByCreatedAtAsc(conversationId);
        if (conversationMessages.isEmpty()) {
            return List.of();
        }
        List<UUID> messageIds = conversationMessages.stream().map(ChatMessage::getId).toList();
        Map<UUID, List<MessageCitation>> citationsByMessage = citations
                .findAllByMessageIdInOrderByCitationNumberAsc(messageIds).stream()
                .collect(Collectors.groupingBy(MessageCitation::getMessageId));

        Set<UUID> documentIds = citationsByMessage.values().stream()
                .flatMap(List::stream)
                .map(MessageCitation::getDocumentId)
                .collect(Collectors.toSet());
        Map<UUID, String> fileNames = fileNames(userId, documentIds);
        Map<UUID, FeedbackRating> ratings = feedback.findAllByMessageIdIn(messageIds).stream()
                .collect(Collectors.toMap(MessageFeedback::getMessageId, MessageFeedback::getRating));

        return conversationMessages.stream()
                .map(message -> {
                    List<CitationResponse> messageCitations = citationsByMessage
                            .getOrDefault(message.getId(), List.of()).stream()
                            .map(citation -> new CitationResponse(
                                    citation.getCitationNumber(),
                                    citation.getDocumentId(),
                                    fileNames.getOrDefault(citation.getDocumentId(), "Deleted document"),
                                    citation.getPage(),
                                    citation.getSnippet(),
                                    citation.getSimilarityScore()))
                            .toList();
                    return new MessageResponse(
                            message.getId(),
                            message.getRole(),
                            message.getContent(),
                            message.getCreatedAt(),
                            messageCitations,
                            ratings.get(message.getId()),
                            isNotFoundAnswer(message, messageCitations));
                })
                .toList();
    }

    /** "Not found" answers are saved without citations, so the text is checked only for those. */
    private boolean isNotFoundAnswer(ChatMessage message, List<CitationResponse> messageCitations) {
        return message.getRole() == MessageRole.ASSISTANT
                && messageCitations.isEmpty()
                && ragAnswerService.isNotFound(message.getContent());
    }

    private Map<UUID, String> fileNames(UUID userId, Collection<UUID> documentIds) {
        if (documentIds.isEmpty()) {
            return Map.of();
        }
        return documents.findAllByUserIdAndIdIn(userId, documentIds).stream()
                .collect(Collectors.toMap(Document::getId, Document::getFileName));
    }
}