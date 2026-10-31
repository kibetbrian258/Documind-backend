package com.backend.documind.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Search settings, read from the "documind.retrieval" section of application.yml.
 *
 * @param topK                how many chunks to retrieve per question (FR-7 starting value: 5)
 * @param similarityThreshold minimum similarity (0 to 1) a chunk needs to count as relevant.
 *                            If nothing reaches it, the answer is "not found" (US-6).
 *                            Starting value; tune it with real questions and the eval suite.
 */
@ConfigurationProperties(prefix = "documind.retrieval")
public record RetrievalProperties(
        @DefaultValue("5") int topK,
        @DefaultValue("0.5") double similarityThreshold) {
}