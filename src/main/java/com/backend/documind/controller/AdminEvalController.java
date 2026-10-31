package com.backend.documind.controller;

import com.backend.documind.dto.EvalRunResponse;
import com.backend.documind.service.EvalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Answer-quality evaluation (FR-17, US-12). ADMIN only: SecurityConfig restricts /api/v1/admin/**.
 */
@RestController
@RequestMapping("/api/v1/admin/evals")
@Tag(name = "Admin: evaluation")
@RequiredArgsConstructor
public class AdminEvalController {

    private final EvalService evalService;

    @PostMapping("/run")
    @Operation(summary = "Run the eval suite",
            description = "Starts answering every question in the eval cases file, using the documents in your "
                    + "account. Returns 202 with a run id straight away; check progress with GET /admin/evals/{runId}.")
    public ResponseEntity<EvalRunResponse> run(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.accepted().body(evalService.start(UUID.fromString(jwt.getSubject())));
    }

    @GetMapping("/{runId}")
    @Operation(summary = "Progress and results of a run",
            description = "While RUNNING, results fill in one question at a time. When COMPLETED, 'summary' has the scores.")
    public EvalRunResponse get(@PathVariable UUID runId) {
        return evalService.get(runId);
    }

    @GetMapping
    @Operation(summary = "Recent runs, newest first (kept until the app restarts)")
    public List<EvalRunResponse> list() {
        return evalService.list();
    }
}