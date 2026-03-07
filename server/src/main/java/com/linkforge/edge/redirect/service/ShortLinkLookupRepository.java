package com.linkforge.edge.redirect.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ShortLinkLookupRepository {

    private final JdbcTemplate jdbcTemplate;

    public ShortLinkLookupRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ShortLinkRow> findByCode(String code) {
        List<ShortLinkRow> rows = jdbcTemplate.query(
                """
                        SELECT id, tenant_id, code, original_url, enabled, expires_at,
                               redirect_status_code, preview_enabled, unavailable_landing_url,
                               query_forward_mode, query_forward_allowlist
                        FROM short_links
                        WHERE code = ?
                          AND archived_at IS NULL
                        LIMIT 1
                        """,
                (rs, rowNum) -> {
                    long id = rs.getLong("id");
                    long tenantId = rs.getLong("tenant_id");
                    String c = rs.getString("code");
                    String originalUrl = rs.getString("original_url");
                    boolean enabled = rs.getBoolean("enabled");
                    Timestamp expiresTs = rs.getTimestamp("expires_at");
                    LocalDateTime expiresAt = expiresTs == null ? null : expiresTs.toLocalDateTime();
                    Integer redirectStatusCode = (Integer) rs.getObject("redirect_status_code");
                    boolean previewEnabled = rs.getBoolean("preview_enabled");
                    String unavailableLandingUrl = rs.getString("unavailable_landing_url");
                    String queryForwardMode = rs.getString("query_forward_mode");
                    String queryForwardAllowlist = rs.getString("query_forward_allowlist");
                    return new ShortLinkRow(
                            id,
                            tenantId,
                            c,
                            originalUrl,
                            enabled,
                            expiresAt,
                            redirectStatusCode,
                            previewEnabled,
                            unavailableLandingUrl,
                            queryForwardMode,
                            queryForwardAllowlist
                    );
                },
                code
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public record ShortLinkRow(
            long id,
            long tenantId,
            String code,
            String originalUrl,
            boolean enabled,
            LocalDateTime expiresAt,
            Integer redirectStatusCode,
            boolean previewEnabled,
            String unavailableLandingUrl,
            String queryForwardMode,
            String queryForwardAllowlist
    ) {
    }
}
