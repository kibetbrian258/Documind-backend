package com.backend.documind.service;

import com.backend.documind.config.ChatProperties;
import com.backend.documind.entity.UsageRecord;
import com.backend.documind.repository.UsageRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Records tokens, estimated cost and timing for every chat call (FR-14).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsageService {

    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000);

    private final UsageRecordRepository usageRecords;
    private final ChatProperties properties;

    @Transactional
    public UsageRecord recordChat(UUID userId, UUID messageId, String model,
                                  int inputTokens, int outputTokens, long latencyMs, long durationMs) {
        BigDecimal cost = estimateCost(model, inputTokens, outputTokens);
        log.debug("Usage for user {}: model={} in={} out={} cost=${} first-token={}ms total={}ms",
                userId, model, inputTokens, outputTokens, cost, latencyMs, durationMs);
        return usageRecords.save(new UsageRecord(
                userId, messageId, model, inputTokens, outputTokens, cost,
                (int) Math.min(latencyMs, Integer.MAX_VALUE), (int) Math.min(durationMs, Integer.MAX_VALUE)));
    }

    /** tokens / 1,000,000 x price per million, for input and output separately. Unlisted models cost 0. */
    BigDecimal estimateCost(String model, int inputTokens, int outputTokens) {
        ChatProperties.ModelPrice price = properties.prices().get(model);
        if (price == null) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }
        BigDecimal input = BigDecimal.valueOf(inputTokens).multiply(price.inputPerMillionTokens());
        BigDecimal output = BigDecimal.valueOf(outputTokens).multiply(price.outputPerMillionTokens());
        return input.add(output).divide(ONE_MILLION, 6, RoundingMode.HALF_UP);
    }
}