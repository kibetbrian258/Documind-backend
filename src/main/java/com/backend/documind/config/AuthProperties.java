package com.backend.documind.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Login settings, read from the "documind.auth" section of application.yml.
 *
 * @param jwtSecret       secret used to sign login tokens; comes from the JWT_SECRET environment variable
 * @param sessionDuration how long a login lasts (US-1: 7 days)
 * @param cookieName      name of the HttpOnly cookie holding the token
 * @param cookieSecure    send the cookie over HTTPS only; switched off for local http development
 */
@ConfigurationProperties(prefix = "documind.auth")
public record AuthProperties(
        String jwtSecret,
        @DefaultValue("7d") Duration sessionDuration,
        @DefaultValue("documind_token") String cookieName,
        @DefaultValue("true") boolean cookieSecure) {

    public AuthProperties {
        // HS256 needs a key of at least 256 bits (32 bytes). Fail at startup, not at first login.
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "documind.auth.jwt-secret is missing or shorter than 32 characters. "
                            + "Set the JWT_SECRET environment variable.");
        }
    }
}