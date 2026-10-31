package com.backend.documind.dto;

import java.util.List;

/**
 * How DocuMind did on one exam question (US-12: "a score per question").
 *
 * @param status          PASSED, FAILED, or SKIPPED (e.g. the document isn't uploaded)
 * @param reason          why it failed or was skipped, in plain words; null when it passed
 * @param answer          what DocuMind replied
 * @param keywordCoverage share of mustContain found in the answer, 0 to 1; null if none were given
 * @param citationCorrect whether a cited page is one of expectedPages; null if no pages were given
 * @param latencyMs       how long the whole answer took
 */
public record EvalCaseResult(
        String id,
        String question,
        Status status,
        String reason,
        String answer,
        boolean expectNotFound,
        boolean answeredNotFound,
        Double keywordCoverage,
        List<String> missingKeywords,
        List<Integer> citedPages,
        List<Integer> expectedPages,
        Boolean citationCorrect,
        Long latencyMs) {

    public enum Status {
        PASSED,
        FAILED,
        SKIPPED
    }
}