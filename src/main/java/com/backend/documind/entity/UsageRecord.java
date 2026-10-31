package com.backend.documind.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Tokens, cost and timing of one AI chat call (FR-14). Maps to the "usage_records" table.
 */
@Entity
@Table(name = "usage_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // required by JPA; not for application code
public class UsageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "message_id")
    private UUID messageId;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens;

    @Column(name = "estimated_cost_usd", nullable = false, precision = 12, scale = 6)
    private BigDecimal estimatedCostUsd;

    /** Time until the first word was streamed. */
    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    /** Time until the answer was complete. */
    @Column(name = "duration_ms", nullable = false)
    private int durationMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UsageRecord(UUID userId, UUID messageId, String model, int inputTokens, int outputTokens,
                       BigDecimal estimatedCostUsd, int latencyMs, int durationMs) {
        this.userId = userId;
        this.messageId = messageId;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.estimatedCostUsd = estimatedCostUsd;
        this.latencyMs = latencyMs;
        this.durationMs = durationMs;
        this.createdAt = Instant.now();
    }
}