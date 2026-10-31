package com.backend.documind.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the application's settings classes so they can be injected.
 * (AuthProperties is registered in SecurityConfig, next to where it's used.)
 */
@Configuration
@EnableConfigurationProperties(DocumentProperties.class)
public class AppConfig {
}