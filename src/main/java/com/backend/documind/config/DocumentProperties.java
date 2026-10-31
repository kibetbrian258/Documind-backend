package com.backend.documind.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;

/**
 * Document settings, read from the "documind.documents" section of application.yml.
 * The 20 MB size limit is set separately under spring.servlet.multipart.
 *
 * @param storageDir              folder where uploaded files are kept
 * @param maxPerUser              how many documents one user may have (FR-3: 50)
 * @param chunkSizeTokens         target chunk size (FR-4 starting value: 500)
 * @param chunkOverlapTokens      tokens repeated from the previous chunk (FR-4 starting value: 50)
 * @param maxConcurrentIngestions documents processed at the same time
 */
@ConfigurationProperties(prefix = "documind.documents")
public record DocumentProperties(
        @DefaultValue("./storage") Path storageDir,
        @DefaultValue("50") int maxPerUser,
        @DefaultValue("500") int chunkSizeTokens,
        @DefaultValue("50") int chunkOverlapTokens,
        @DefaultValue("2") int maxConcurrentIngestions) {
}