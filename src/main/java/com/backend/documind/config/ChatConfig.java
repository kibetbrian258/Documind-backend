package com.backend.documind.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers ChatProperties so it can be injected.
 * (Harmless if @ConfigurationPropertiesScan is also on the main class; Spring registers it only once.)
 */
@Configuration
@EnableConfigurationProperties(ChatProperties.class)
public class ChatConfig {
}