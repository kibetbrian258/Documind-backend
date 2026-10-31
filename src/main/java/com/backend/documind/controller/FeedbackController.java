package com.backend.documind.controller;

import com.backend.documind.dto.FeedbackRequest;
import com.backend.documind.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/messages")
@Tag(name = "Chat")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping("/{id}/feedback")
    @Operation(summary = "Rate an answer thumbs up or down",
            description = "Use the messageId from the 'citations' event or from GET /conversations/{id}/messages. "
                    + "Rating the same answer again replaces the earlier rating.")
    public ResponseEntity<Void> rate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                     @Valid @RequestBody FeedbackRequest request) {
        feedbackService.rate(UUID.fromString(jwt.getSubject()), id, request);
        return ResponseEntity.noContent().build();
    }
}