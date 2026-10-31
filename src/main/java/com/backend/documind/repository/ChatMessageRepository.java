package com.backend.documind.repository;

import com.backend.documind.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Only call these after checking the conversation belongs to the current user
 * (ConversationService.getOwned); messages have no user id of their own.
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    /** The whole conversation, oldest first. */
    List<ChatMessage> findAllByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    /** The most recent messages, newest first; the page size sets how many (chat memory, FR-10). */
    List<ChatMessage> findByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable page);
}