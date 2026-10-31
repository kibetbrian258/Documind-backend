package com.backend.documind.service;

import com.backend.documind.config.ChatProperties;
import com.backend.documind.dto.AdminUsageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Numbers for the admin dashboard (US-11, FR-16).
 *
 * These are totals across all users, so plain SQL is the clearest tool: the
 * database groups and adds up, and nothing is loaded row by row into Java.
 * Days are counted in the same time zone as the daily question limit.
 */
@Service
@RequiredArgsConstructor
public class AdminUsageService {

    private static final String BY_USER_AND_DAY = """
            SELECT (u.created_at AT TIME ZONE :zone)::date AS day,
                   u.user_id,
                   us.email,
                   count(*)                     AS questions,
                   sum(u.input_tokens)          AS input_tokens,
                   sum(u.output_tokens)         AS output_tokens,
                   sum(u.estimated_cost_usd)    AS cost,
                   round(avg(u.latency_ms))     AS avg_first_token_ms
            FROM usage_records u
            JOIN users us ON us.id = u.user_id
            WHERE u.created_at >= :from
            GROUP BY day, u.user_id, us.email
            ORDER BY day DESC, cost DESC, questions DESC
            """;

    private static final String TOTALS = """
            SELECT count(*)                                                 AS questions,
                   coalesce(sum(input_tokens), 0)                           AS input_tokens,
                   coalesce(sum(output_tokens), 0)                          AS output_tokens,
                   coalesce(sum(estimated_cost_usd), 0)                     AS cost,
                   percentile_cont(0.5) WITHIN GROUP (ORDER BY latency_ms)  AS median_first_token_ms,
                   count(DISTINCT user_id)                                  AS active_users
            FROM usage_records
            WHERE created_at >= :from
            """;

    private static final String OVERVIEW = """
            SELECT (SELECT count(*) FROM users)                                        AS total_users,
                   (SELECT count(*) FROM users WHERE created_at >= :from)              AS new_users,
                   (SELECT count(*) FROM documents WHERE status = 'READY')             AS documents_ready,
                   (SELECT count(*) FROM documents WHERE status = 'PROCESSING')        AS documents_processing,
                   (SELECT count(*) FROM documents WHERE status = 'FAILED')            AS documents_failed,
                   (SELECT count(*) FROM message_feedback
                     WHERE rating = 'UP'   AND updated_at >= :from)                    AS thumbs_up,
                   (SELECT count(*) FROM message_feedback
                     WHERE rating = 'DOWN' AND updated_at >= :from)                    AS thumbs_down
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ChatProperties chatProperties;

    /** @param days 1 to 90; 7 means today and the 6 days before it */
    @Transactional(readOnly = true)
    public AdminUsageResponse usage(int days) {
        ZoneId zone = ZoneId.of(chatProperties.dailyLimitTimeZone());
        Instant from = LocalDate.now(zone).minusDays(days - 1L).atStartOfDay(zone).toInstant();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("zone", zone.getId())
                .addValue("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC));

        List<AdminUsageResponse.UserDay> byDay = jdbc.query(BY_USER_AND_DAY, params, (rs, row) ->
                new AdminUsageResponse.UserDay(
                        rs.getObject("day", LocalDate.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("email"),
                        rs.getLong("questions"),
                        rs.getLong("input_tokens"),
                        rs.getLong("output_tokens"),
                        money(rs.getBigDecimal("cost")),
                        nullableLong(rs, "avg_first_token_ms")));

        AdminUsageResponse.Totals totals = jdbc.queryForObject(TOTALS, params, (rs, row) -> {
            long questions = rs.getLong("questions");
            BigDecimal cost = money(rs.getBigDecimal("cost"));
            return new AdminUsageResponse.Totals(
                    questions,
                    rs.getLong("input_tokens"),
                    rs.getLong("output_tokens"),
                    cost,
                    questions == 0 ? null : cost.divide(BigDecimal.valueOf(questions), 6, RoundingMode.HALF_UP),
                    nullableLong(rs, "median_first_token_ms"),
                    rs.getLong("active_users"));
        });

        AdminUsageResponse.Overview overview = jdbc.queryForObject(OVERVIEW, params, (rs, row) -> {
            long up = rs.getLong("thumbs_up");
            long down = rs.getLong("thumbs_down");
            return new AdminUsageResponse.Overview(
                    rs.getLong("total_users"),
                    rs.getLong("new_users"),
                    rs.getLong("documents_ready"),
                    rs.getLong("documents_processing"),
                    rs.getLong("documents_failed"),
                    up,
                    down,
                    up + down == 0 ? null : Math.round(1000.0 * up / (up + down)) / 10.0);
        });

        return new AdminUsageResponse(days, zone.getId(), from, totals, byDay, overview);
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(6, RoundingMode.HALF_UP);
    }

    /** Reads a whole number that may be NULL (e.g. a median over no rows). */
    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : Math.round(value);
    }
}