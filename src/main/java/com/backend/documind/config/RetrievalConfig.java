package com.backend.documind.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers RetrievalProperties so it can be injected.
 * (Harmless if @ConfigurationPropertiesScan is also on the main class; Spring registers it only once.)
 */
@Configuration
@EnableConfigurationProperties(RetrievalProperties.class)
public class RetrievalConfig {
}