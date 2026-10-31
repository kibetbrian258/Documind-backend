package com.backend.documind.service;

import com.backend.documind.dto.FeedbackRequest;
import com.backend.documind.entity.ChatMessage;
import com.backend.documind.entity.MessageFeedback;
import com.backend.documind.entity.MessageRole;
import com.backend.documind.repository.ChatMessageRepository;
import com.backend.documind.repository.MessageFeedbackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Thumbs up / down on answers (FR-20), stored for review and for the
 * "user satisfaction" figure on the admin dashboard.
 *
 * Users can only rate answers in their own conversations; anything else
 * gives "not found", like everywhere else.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final ChatMessageRepository messages;
    private final MessageFeedbackRepository feedback;
    private final ConversationService conversationService;

    /** Saves the rating, or replaces an earlier one for the same answer. */
    @Transactional
    public void rate(UUID userId, UUID messageId, FeedbackRequest request) {
        ChatMessage message = messages.findById(messageId).orElseThrow(FeedbackService::notFound);
        // Throws "not found" if the conversation isn't this user's, so other users' ids reveal nothing.
        conversationService.getOwned(userId, message.getConversationId());

        if (message.getRole() != MessageRole.ASSISTANT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only answers can be rated, not questions.");
        }

        String comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().strip();
        feedback.findByMessageId(messageId).ifPresentOrElse(
                existing -> existing.change(request.rating(), comment),
                () -> feedback.save(new MessageFeedback(messageId, userId, request.rating(), comment)));
        log.info("User {} rated answer {} {}", userId, messageId, request.rating());
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found.");
    }
}