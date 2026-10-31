package com.backend.documind.dto;

/**
 * The headline numbers of an evaluation run, matching the PRD's success metrics (section 12).
 * Percentages are 0-100; null when there were no questions of that kind.
 *
 * @param passRatePercent          passed / (all questions that weren't skipped)          (US-12)
 * @param answerCorrectnessPercent answerable questions answered correctly                 (target: 85%+)
 * @param citationAccuracyPercent  answers citing a page that really contains the answer   (target: 90%+)
 * @param notFoundAccuracyPercent  unanswerable questions correctly answered "not found"   (target: 100%)
 * @param medianLatencyMs          typical time for a whole answer
 */
public record EvalSummary(
        int total,
        int passed,
        int failed,
        int skipped,
        Double passRatePercent,
        Double answerCorrectnessPercent,
        Double citationAccuracyPercent,
        Double notFoundAccuracyPercent,
        Long medianLatencyMs) {
}