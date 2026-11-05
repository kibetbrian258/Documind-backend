package com.backend.documind.service;

import com.backend.documind.config.ChatProperties;
import com.backend.documind.dto.AdminUserResponse;
import com.backend.documind.entity.Role;
import com.backend.documind.entity.User;
import com.backend.documind.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * The admin's Users page: everyone's daily limit and how much of it they've
 * used today, and changing a limit (FR-15, US-11).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final String SELECT_USERS = """
            SELECT u.id, u.full_name, u.email, u.role, u.daily_limit, u.created_at,
                   (SELECT count(*) FROM usage_records r
                     WHERE r.user_id = u.id AND r.created_at >= :today) AS questions_today,
                   (SELECT count(*) FROM documents d WHERE d.user_id = u.id) AS documents
            FROM users u
            """;
    private static final String ALL_USERS = SELECT_USERS + "ORDER BY u.created_at DESC";
    private static final String ONE_USER = SELECT_USERS + "WHERE u.id = :id";

    private static final RowMapper<AdminUserResponse> USER_ROW = (rs, row) -> new AdminUserResponse(
            rs.getObject("id", UUID.class),
            rs.getString("full_name"),
            rs.getString("email"),
            Role.valueOf(rs.getString("role")),
            rs.getInt("daily_limit"),
            rs.getLong("questions_today"),
            rs.getLong("documents"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant());

    private final NamedParameterJdbcTemplate jdbc;
    private final UserRepository users;
    private final ChatProperties chatProperties;

    @Transactional(readOnly = true)
    public List<AdminUserResponse> list() {
        return jdbc.query(ALL_USERS, params(), USER_ROW);
    }

    @Transactional
    public AdminUserResponse updateDailyLimit(UUID userId, int dailyLimit) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        int previous = user.getDailyLimit();
        user.changeDailyLimit(dailyLimit);
        users.flush(); // so the query below sees the new limit
        log.info("Daily limit of user {} changed from {} to {}", userId, previous, dailyLimit);
        return jdbc.queryForObject(ONE_USER, params().addValue("id", userId), USER_ROW);
    }

    private MapSqlParameterSource params() {
        return new MapSqlParameterSource()
                .addValue("today", OffsetDateTime.ofInstant(startOfToday(), ZoneOffset.UTC));
    }

    private Instant startOfToday() {
        ZoneId zone = ZoneId.of(chatProperties.dailyLimitTimeZone());
        return LocalDate.now(zone).atStartOfDay(zone).toInstant();
    }
}