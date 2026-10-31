package com.backend.documind.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Evaluation settings, read from the "documind.evals" section of application.yml.
 *
 * @param casesFile          where the exam questions are. "classpath:" means inside the project
 *                           (src/main/resources); "file:" means a file on disk.
 * @param minKeywordCoverage share of mustContain words an answer needs to pass, 0 to 1
 */
@ConfigurationProperties(prefix = "documind.evals")
public record EvalProperties(
        @DefaultValue("classpath:evals/eval-cases.json") String casesFile,
        @DefaultValue("0.6") double minKeywordCoverage) {
}