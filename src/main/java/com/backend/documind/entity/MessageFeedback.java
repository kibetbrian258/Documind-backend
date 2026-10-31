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
 * A user's rating of one answer. Maps to the "message_feedback" table.
 */
@Entity
@Table(name = "message_feedback")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // required by JPA; not for application code
public class MessageFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Becomes null if the conversation is deleted; the rating itself is kept for statistics. */
    @Column(name = "message_id")
    private UUID messageId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FeedbackRating rating;

    @Column(length = 1000)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public MessageFeedback(UUID messageId, UUID userId, FeedbackRating rating, String comment) {
        this.messageId = messageId;
        this.userId = userId;
        this.rating = rating;
        this.comment = comment;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /** The user changed their mind, e.g. from thumbs up to thumbs down. */
    public void change(FeedbackRating rating, String comment) {
        this.rating = rating;
        this.comment = comment;
        this.updatedAt = Instant.now();
    }
}