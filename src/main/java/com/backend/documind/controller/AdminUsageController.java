package com.backend.documind.controller;

import com.backend.documind.dto.AdminUsageResponse;
import com.backend.documind.service.AdminUsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Usage and cost dashboard (US-11, FR-16). ADMIN only: SecurityConfig restricts /api/v1/admin/**.
 */
@RestController
@RequestMapping("/api/v1/admin/usage")
@Tag(name = "Admin: usage")
@RequiredArgsConstructor
public class AdminUsageController {

    private final AdminUsageService adminUsageService;

    @GetMapping
    @Operation(summary = "Usage, cost, errors and users",
            description = "Totals for the period, one row per user per day, and an overview of users, "
                    + "document health and answer ratings. 'days' covers today and the days before it (1 to 90).")
    public AdminUsageResponse usage(@RequestParam(defaultValue = "7") int days) {
        if (days < 1 || days > 90) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "days must be between 1 and 90.");
        }
        return adminUsageService.usage(days);
    }
}