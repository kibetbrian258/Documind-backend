package com.backend.documind.controller;

import com.backend.documind.dto.UserResponse;
import com.backend.documind.entity.User;
import com.backend.documind.service.AuthService;
import com.backend.documind.service.QuotaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Account")
@RequiredArgsConstructor
public class MeController {

    private final AuthService authService;
    private final QuotaService quotaService;

    @GetMapping
    @Operation(summary = "Current user, remaining daily questions and when they refill")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        // The token's subject is the user's id, set by JwtService at login.
        UUID userId = UUID.fromString(jwt.getSubject());
        User user = authService.getActiveUser(userId);
        return UserResponse.from(user, quotaService.today(user));
    }
}