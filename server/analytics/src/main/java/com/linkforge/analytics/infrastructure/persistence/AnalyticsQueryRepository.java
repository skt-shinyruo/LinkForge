package com.linkforge.analytics.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class AnalyticsQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final String TOP_LINKS_SQL_ORDER_BY_PV = """
            SELECT s.link_id AS link_id, SUM(s.pv) AS pv, SUM(s.uv) AS uv
            FROM link_stats_daily s
            WHERE s.tenant_id = ?
              AND s.day >= ?
              AND s.day <= ?
            GROUP BY s.link_id
            ORDER BY pv DESC, uv DESC, s.link_id ASC
            LIMIT ?
            """;

    private static final String TOP_LINKS_SQL_ORDER_BY_UV = """
            SELECT s.link_id AS link_id, SUM(s.pv) AS pv, SUM(s.uv) AS uv
            FROM link_stats_daily s
            WHERE s.tenant_id = ?
              AND s.day >= ?
              AND s.day <= ?
            GROUP BY s.link_id
            ORDER BY uv DESC, pv DESC, s.link_id ASC
            LIMIT ?
            """;

    public AnalyticsQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DailyStatRow> linkDaily(long tenantId, long linkId, LocalDate from, LocalDate to) {
        return jdbcTemplate.query(
                """
                        SELECT day, pv, uv
                        FROM link_stats_daily
                        WHERE tenant_id = ?
                          AND link_id = ?
                          AND day >= ?
                          AND day <= ?
                        ORDER BY day ASC
                        """,
                (rs, rowNum) -> new DailyStatRow(
                        rs.getDate("day").toLocalDate(),
                        rs.getLong("pv"),
                        rs.getLong("uv")
                ),
                tenantId, linkId, Date.valueOf(from), Date.valueOf(to)
        );
    }

    public List<DailyStatRow> tenantDaily(long tenantId, LocalDate from, LocalDate to) {
        return jdbcTemplate.query(
                """
                        SELECT day, SUM(pv) AS pv, SUM(uv) AS uv
                        FROM link_stats_daily
                        WHERE tenant_id = ?
                          AND day >= ?
                          AND day <= ?
                        GROUP BY day
                        ORDER BY day ASC
                        """,
                (rs, rowNum) -> new DailyStatRow(
                        rs.getDate("day").toLocalDate(),
                        rs.getLong("pv"),
                        rs.getLong("uv")
                ),
                tenantId, Date.valueOf(from), Date.valueOf(to)
        );
    }

    public List<TopLinkAggRow> topLinksOrderByPv(long tenantId, LocalDate from, LocalDate to, int limit) {
        return jdbcTemplate.query(
                TOP_LINKS_SQL_ORDER_BY_PV,
                (rs, rowNum) -> new TopLinkAggRow(
                        rs.getLong("link_id"),
                        rs.getLong("pv"),
                        rs.getLong("uv")
                ),
                tenantId, Date.valueOf(from), Date.valueOf(to), limit
        );
    }

    public List<TopLinkAggRow> topLinksOrderByUv(long tenantId, LocalDate from, LocalDate to, int limit) {
        return jdbcTemplate.query(
                TOP_LINKS_SQL_ORDER_BY_UV,
                (rs, rowNum) -> new TopLinkAggRow(
                        rs.getLong("link_id"),
                        rs.getLong("pv"),
                        rs.getLong("uv")
                ),
                tenantId, Date.valueOf(from), Date.valueOf(to), limit
        );
    }

    public Long linkDimTotalPv(long tenantId, long linkId, LocalDate from, LocalDate to, String dimType) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT COALESCE(SUM(pv), 0) AS total_pv
                        FROM link_stats_dim_daily
                        WHERE tenant_id = ?
                          AND link_id = ?
                          AND day >= ?
                          AND day <= ?
                          AND dim_type = ?
                        """,
                Long.class,
                tenantId, linkId, Date.valueOf(from), Date.valueOf(to), dimType
        );
    }

    public List<DimensionRow> linkDimRows(long tenantId, long linkId, LocalDate from, LocalDate to, String dimType, int limit) {
        return jdbcTemplate.query(
                """
                        SELECT dim_value, SUM(pv) AS pv, SUM(uv) AS uv
                        FROM link_stats_dim_daily
                        WHERE tenant_id = ?
                          AND link_id = ?
                          AND day >= ?
                          AND day <= ?
                          AND dim_type = ?
                        GROUP BY dim_value
                        ORDER BY pv DESC, uv DESC, dim_value ASC
                        LIMIT ?
                        """,
                (rs, rowNum) -> new DimensionRow(
                        rs.getString("dim_value"),
                        rs.getLong("pv"),
                        rs.getLong("uv")
                ),
                tenantId, linkId, Date.valueOf(from), Date.valueOf(to), dimType, limit
        );
    }

    public List<VisitEventRow> linkEvents(long tenantId, long linkId, LocalDateTime from, LocalDateTime to, int limit) {
        return jdbcTemplate.query(
                """
                        SELECT occurred_at, request_id, ip_hash,
                               ua_raw, ua_family, os_family, device_type,
                               referer_domain, language,
                               utm_source, utm_medium, utm_campaign
                        FROM link_visit_events
                        WHERE tenant_id = ?
                          AND link_id = ?
                          AND occurred_at >= ?
                          AND occurred_at <= ?
                        ORDER BY occurred_at DESC, id DESC
                        LIMIT ?
                        """,
                (rs, rowNum) -> new VisitEventRow(
                        rs.getObject("occurred_at", LocalDateTime.class),
                        rs.getString("request_id"),
                        rs.getString("ip_hash"),
                        rs.getString("ua_raw"),
                        rs.getString("ua_family"),
                        rs.getString("os_family"),
                        rs.getString("device_type"),
                        rs.getString("referer_domain"),
                        rs.getString("language"),
                        rs.getString("utm_source"),
                        rs.getString("utm_medium"),
                        rs.getString("utm_campaign")
                ),
                tenantId, linkId, from, to, limit
        );
    }

    public record DailyStatRow(LocalDate day, long pv, long uv) {
    }

    public record TopLinkAggRow(long linkId, long pv, long uv) {
    }

    public record DimensionRow(String value, long pv, long uv) {
    }

    public record VisitEventRow(
            LocalDateTime occurredAt,
            String requestId,
            String ipHash,
            String userAgentRaw,
            String userAgentFamily,
            String osFamily,
            String deviceType,
            String refererDomain,
            String language,
            String utmSource,
            String utmMedium,
            String utmCampaign
    ) {
    }
}

