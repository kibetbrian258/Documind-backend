package com.backend.documind.service;

import com.backend.documind.config.RetrievalProperties;
import com.backend.documind.dto.RetrievedChunk;
import com.backend.documind.entity.Document;
import com.backend.documind.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Finds the passages most relevant to a question (FR-7, FR-11).
 *
 * PRIVACY RULE: this is the only class that searches the vector store, and
 * every search is filtered to the logged-in user's chunks. A user id is
 * required; there is no way to call it without one. If this class is correct,
 * no answer can ever be built from another user's documents.
 *
 * As a second safety net, results are checked again after the search, and
 * anything not owned by the user is dropped and logged as an error.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalService {

    private final VectorStore vectorStore;
    private final DocumentRepository documents;
    private final RetrievalProperties properties;

    /**
     * Searches the user's documents with the configured top-K and similarity threshold.
     *
     * @param documentIds optional; null or empty means all of the user's documents
     * @return the best matches, most similar first; empty means "not found"
     */
    public List<RetrievedChunk> retrieve(UUID userId, String question, Collection<UUID> documentIds) {
        return retrieve(userId, question, documentIds, properties.similarityThreshold());
    }

    /** Same as above with a custom threshold; used by the development search endpoint for tuning. */
    public List<RetrievedChunk> retrieve(UUID userId, String question, Collection<UUID> documentIds,
                                         double similarityThreshold) {
        Objects.requireNonNull(userId, "userId is required: every search is limited to one user");

        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(properties.topK())
                .similarityThreshold(similarityThreshold)
                .filterExpression(ownedBy(userId, documentIds))
                .build();

        List<org.springframework.ai.document.Document> hits = vectorStore.similaritySearch(request);

        List<RetrievedChunk> results = toRetrievedChunks(userId, hits);
        if (log.isDebugEnabled()) {
            log.debug("Search by user {} found {} chunk(s), similarities {}", userId, results.size(),
                    results.stream().map(c -> String.format("%.3f", c.similarity())).toList());
        }
        return results;
    }

    /** user_id == me, and optionally document_id IN (selected documents). */
    private static Filter.Expression ownedBy(UUID userId, Collection<UUID> documentIds) {
        FilterExpressionBuilder filter = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op mine = filter.eq(TextChunker.USER_ID, userId.toString());
        if (documentIds == null || documentIds.isEmpty()) {
            return mine.build();
        }
        // Selecting someone else's document id simply matches nothing, because user_id must match too.
        List<Object> selected = new ArrayList<>();
        documentIds.forEach(id -> selected.add(id.toString()));
        return filter.and(mine, filter.in(TextChunker.DOCUMENT_ID, selected)).build();
    }

    private List<RetrievedChunk> toRetrievedChunks(UUID userId,
                                                   List<org.springframework.ai.document.Document> hits) {
        if (hits.isEmpty()) {
            return List.of();
        }
        String owner = userId.toString();

        // Safety net: the filter should already guarantee this.
        List<org.springframework.ai.document.Document> owned = hits.stream()
                .filter(hit -> {
                    boolean isOwned = owner.equals(hit.getMetadata().get(TextChunker.USER_ID));
                    if (!isOwned) {
                        log.error("PRIVACY: search for user {} returned chunk {} owned by someone else; dropped",
                                userId, hit.getId());
                    }
                    return isOwned;
                })
                .toList();

        // Look up file names in one query, again limited to this user's documents.
        Set<UUID> documentIds = owned.stream()
                .map(hit -> UUID.fromString((String) hit.getMetadata().get(TextChunker.DOCUMENT_ID)))
                .collect(Collectors.toSet());
        Map<UUID, Document> byId = documents.findAllByUserIdAndIdIn(userId, documentIds).stream()
                .collect(Collectors.toMap(Document::getId, Function.identity()));

        List<RetrievedChunk> results = new ArrayList<>();
        for (var hit : owned) {
            Map<String, Object> metadata = hit.getMetadata();
            UUID documentId = UUID.fromString((String) metadata.get(TextChunker.DOCUMENT_ID));
            Document document = byId.get(documentId);
            if (document == null) {
                continue; // deleted a moment ago
            }
            results.add(new RetrievedChunk(
                    hit.getId(),
                    documentId,
                    document.getFileName(),
                    metadata.get(TextChunker.PAGE) instanceof Number page ? page.intValue() : null,
                    metadata.get(TextChunker.CHUNK_INDEX) instanceof Number index ? index.intValue() : 0,
                    Objects.requireNonNullElse(hit.getText(), ""),
                    Objects.requireNonNullElse(hit.getScore(), 0.0)));
        }
        return results;
    }
}