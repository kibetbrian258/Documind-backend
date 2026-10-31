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

import java.util.UUID;

/**
 * A source behind an answer: which document and page a [n] in the answer refers to (FR-9).
 * Maps to the "message_citations" table.
 */
@Entity
@Table(name = "message_citations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // required by JPA; not for application code
public class MessageCitation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "message_id", nullable = false, updatable = false)
    private UUID messageId;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    /** The number shown in the answer, e.g. 2 for "[2]". */
    @Column(name = "citation_number", nullable = false)
    private int citationNumber;

    /** Null for DOCX and TXT, which have no pages. */
    @Column(name = "page")
    private Integer page;

    @Column(name = "chunk_id", nullable = false)
    private UUID chunkId;

    @Column(name = "similarity_score", nullable = false)
    private double similarityScore;

    @Column(nullable = false)
    private String snippet;

    public MessageCitation(UUID messageId, UUID documentId, int citationNumber, Integer page,
                           UUID chunkId, double similarityScore, String snippet) {
        this.messageId = messageId;
        this.documentId = documentId;
        this.citationNumber = citationNumber;
        this.page = page;
        this.chunkId = chunkId;
        this.similarityScore = similarityScore;
        this.snippet = snippet;
    }
}