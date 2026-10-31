package com.backend.documind.repository;

import com.backend.documind.entity.UsageRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

/**
 * Step 9 adds the admin dashboard totals.
 */
public interface UsageRecordRepository extends JpaRepository<UsageRecord, UUID> {

    /** AI calls by one user since a moment in time; used for the daily question limit. */
    long countByUserIdAndCreatedAtGreaterThanEqual(UUID userId, Instant since);
}