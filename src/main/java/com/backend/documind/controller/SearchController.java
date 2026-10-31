package com.backend.documind.controller;

import com.backend.documind.dto.RetrievedChunk;
import com.backend.documind.dto.SearchQuery;
import com.backend.documind.service.RetrievalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * DEVELOPMENT ONLY: shows exactly which passages a question would retrieve,
 * with their similarity scores. Used to test privacy and tune the threshold
 * before the chat exists.
 *
 * @Profile("local") means this endpoint doesn't exist outside local development.
 */
@Profile("local")
@RestController
@RequestMapping("/api/v1/search")
@Tag(name = "Search (development only)")
@RequiredArgsConstructor
public class SearchController {

    private final RetrievalService retrievalService;

    @PostMapping
    @Operation(summary = "See which passages a question retrieves",
            description = "Only searches your own documents. Set minSimilarity to 0 to see every score while tuning.")
    public List<RetrievedChunk> search(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SearchQuery query) {
        UUID userId = UUID.fromString(jwt.getSubject());
        if (query.minSimilarity() != null) {
            return retrievalService.retrieve(userId, query.question(), query.documentIds(), query.minSimilarity());
        }
        return retrievalService.retrieve(userId, query.question(), query.documentIds());
    }
}