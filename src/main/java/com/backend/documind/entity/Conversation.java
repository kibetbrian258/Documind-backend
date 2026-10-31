package com.backend.documind.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * A chat thread belonging to one user. Maps to the "conversations" table.
 */
@Entity
@Table(name = "conversations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // required by JPA; not for application code
public class Conversation {

    private static final int MAX_TITLE_LENGTH = 80;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(length = 200)
    private String title;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Conversation(UUID userId, String title) {
        this.userId = userId;
        this.title = title;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /** Moves the conversation to the top of the sidebar. */
    public void touch() {
        this.updatedAt = Instant.now();
    }

    /** US-9: the user gives the conversation their own name. */
    public void rename(String newTitle) {
        this.title = newTitle.strip();
        touch();
    }

    /** Names an untitled conversation after its first question (PRD section 9). */
    public void titleFromFirstQuestion(String question) {
        if (title != null && !title.isBlank()) {
            return;
        }
        String oneLine = question.strip().replaceAll("\\s+", " ");
        this.title = oneLine.length() <= MAX_TITLE_LENGTH
                ? oneLine
                : oneLine.substring(0, MAX_TITLE_LENGTH - 1).strip() + "…";
    }
}