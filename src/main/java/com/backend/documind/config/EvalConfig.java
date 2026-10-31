package com.backend.documind.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers EvalProperties so it can be injected.
 * (Harmless if @ConfigurationPropertiesScan is also on the main class; Spring registers it only once.)
 */
@Configuration
@EnableConfigurationProperties(EvalProperties.class)
public class EvalConfig {
}