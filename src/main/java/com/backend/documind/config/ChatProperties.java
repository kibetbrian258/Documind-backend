package com.backend.documind.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Map;

/**
 * Chat settings, read from the "documind.chat" section of application.yml.
 *
 * @param memoryMessages  how many earlier messages are sent along for follow-ups (FR-10: 10)
 * @param streamTimeout   longest an answer may take before the stream is closed
 * @param notFoundMessage the honest reply when no passage is relevant (US-6)
 * @param prices          price per million tokens for each model name, for cost estimates (FR-14).
 *                        Models not listed (like local Ollama models) cost 0.
 * @param dailyLimitTimeZone where "today" starts and ends for the daily question limit (FR-15),
 *                        e.g. "UTC" or "Africa/Nairobi"
 */
@ConfigurationProperties(prefix = "documind.chat")
public record ChatProperties(
        @DefaultValue("10") int memoryMessages,
        @DefaultValue("5m") Duration streamTimeout,
        @DefaultValue("I couldn't find that in your documents.") String notFoundMessage,
        Map<String, ModelPrice> prices,
        @DefaultValue("UTC") String dailyLimitTimeZone) {

    public ChatProperties {
        prices = prices == null ? Map.of() : Map.copyOf(prices);
        ZoneId.of(dailyLimitTimeZone); // fails at startup if the time zone name is wrong
    }

    /** US dollars per one million tokens. */
    public record ModelPrice(BigDecimal inputPerMillionTokens, BigDecimal outputPerMillionTokens) {
    }
}