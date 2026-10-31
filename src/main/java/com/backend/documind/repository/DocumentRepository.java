package com.backend.documind.repository;

import com.backend.documind.entity.Document;
import com.backend.documind.entity.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Every lookup includes the owner's userId, so one user can never load
 * another user's document by guessing its id.
 */
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Document> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);

    /** Several of one user's documents at once; ids belonging to other users are simply not returned. */
    List<Document> findAllByUserIdAndIdIn(UUID userId, Collection<UUID> ids);

    /** Used at startup to resume documents whose processing was interrupted. */
    List<Document> findAllByStatus(DocumentStatus status);
}