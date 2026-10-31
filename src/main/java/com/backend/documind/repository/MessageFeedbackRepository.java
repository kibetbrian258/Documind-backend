package com.backend.documind.repository;

import com.backend.documind.entity.MessageFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Only call these after checking the message's conversation belongs to the current user.
 */
public interface MessageFeedbackRepository extends JpaRepository<MessageFeedback, UUID> {

    Optional<MessageFeedback> findByMessageId(UUID messageId);

    List<MessageFeedback> findAllByMessageIdIn(Collection<UUID> messageIds);
}