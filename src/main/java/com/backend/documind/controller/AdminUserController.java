package com.backend.documind.controller;

import com.backend.documind.dto.AdminUserResponse;
import com.backend.documind.dto.UpdateDailyLimitRequest;
import com.backend.documind.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Users and their daily question limits (FR-15, US-11). ADMIN only: SecurityConfig restricts /api/v1/admin/**.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin: users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    @Operation(summary = "All users, newest first, with today's questions and their daily limit")
    public List<AdminUserResponse> list() {
        return adminUserService.list();
    }

    @PatchMapping("/{id}/daily-limit")
    @Operation(summary = "Change a user's daily question limit",
            description = "0 to 1000. 0 pauses asking for that user. Takes effect on their next question.")
    public AdminUserResponse updateDailyLimit(@PathVariable UUID id,
                                              @Valid @RequestBody UpdateDailyLimitRequest request) {
        return adminUserService.updateDailyLimit(id, request.dailyLimit());
    }
}