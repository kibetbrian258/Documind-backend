package com.backend.documind.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Turns on @Async, used to process uploads in the background.
 *
 * Because spring.threads.virtual.enabled is true, Spring Boot runs @Async work
 * on Java 21 virtual threads, which are cheap enough to start one per upload.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
