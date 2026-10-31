package com.backend.documind.dto;

import java.util.List;

/**
 * One exam question with its known answer, read from the eval cases file.
 *
 * @param id             short name shown in results, e.g. "report-01"
 * @param question       what to ask
 * @param document       file name of the document the question is about; the question
 *                       searches only that document. Leave out to search all documents.
 * @param expectNotFound true if the documents do NOT contain the answer, so the correct
 *                       reply is "not found"
 * @param mustContain    words or short phrases a correct answer contains (case doesn't matter)
 * @param expectedPages  PDF page(s) where the answer is; a citation to any of them counts as correct
 * @param notes          free text for you; ignored by the evaluation
 */
public record EvalCase(
        String id,
        String question,
        String document,
        Boolean expectNotFound,
        List<String> mustContain,
        List<Integer> expectedPages,
        String notes) {

    public boolean shouldBeNotFound() {
        return Boolean.TRUE.equals(expectNotFound);
    }

    public List<String> keywords() {
        return mustContain == null ? List.of() : mustContain;
    }

    public List<Integer> pages() {
        return expectedPages == null ? List.of() : expectedPages;
    }
}