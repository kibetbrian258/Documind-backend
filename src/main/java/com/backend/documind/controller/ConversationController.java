package com.backend.documind.controller;

import com.backend.documind.dto.AskRequest;
import com.backend.documind.dto.ConversationResponse;
import com.backend.documind.dto.CreateConversationRequest;
import com.backend.documind.dto.MessageResponse;
import com.backend.documind.dto.RenameConversationRequest;
import com.backend.documind.service.ChatService;
import com.backend.documind.service.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/conversations")
@Tag(name = "Chat")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final ChatService chatService;

    @PostMapping
    @Operation(summary = "Start a conversation", description = "The title is optional; the first question becomes the title.")
    public ResponseEntity<ConversationResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                       @Valid @RequestBody(required = false) CreateConversationRequest request) {
        String title = request == null ? null : request.title();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ConversationResponse.from(conversationService.create(userId(jwt), title)));
    }

    @GetMapping
    @Operation(summary = "List my conversations, most recently used first")
    public List<ConversationResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return conversationService.list(userId(jwt)).stream().map(ConversationResponse::from).toList();
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Rename a conversation")
    public ConversationResponse rename(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                       @Valid @RequestBody RenameConversationRequest request) {
        return ConversationResponse.from(conversationService.rename(userId(jwt), id, request.title()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a conversation with all its messages")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        conversationService.delete(userId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/messages")
    @Operation(summary = "Messages in a conversation, with their citations")
    public List<MessageResponse> messages(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return conversationService.messages(userId(jwt), id);
    }

    // No "produces" here on purpose: the SseEmitter sets text/event-stream itself, and leaving it out
    // lets errors raised before streaming starts (404, invalid input) still be returned as JSON.
    @PostMapping(path = "/{id}/ask", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Ask a question; the answer streams back as Server-Sent Events",
            description = "Events: 'searched' (once, how many passages were found), 'token' (a piece of the answer, "
                    + "many times), 'citations' (once, at the end; notFound=true for the \"couldn't find that\" reply), "
                    + "'error' (only if something went wrong). Swagger UI shows all events once the answer is complete.")
    public SseEmitter ask(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                          @Valid @RequestBody AskRequest request) {
        return chatService.ask(userId(jwt), id, request);
    }

    /** The token's subject is the user's id, set by JwtService at login. */
    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}