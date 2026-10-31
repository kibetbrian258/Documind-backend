package com.backend.documind.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An evaluation run. While it's RUNNING, completedCases and results grow as each
 * question is answered; the summary appears once it's COMPLETED.
 */
public record EvalRunResponse(
        UUID id,
        RunStatus status,
        Instant startedAt,
        Instant finishedAt,
        int totalCases,
        int completedCases,
        EvalSummary summary,
        String error,
        List<EvalCaseResult> results) {

    public enum RunStatus {
        RUNNING,
        COMPLETED,
        FAILED
    }
}