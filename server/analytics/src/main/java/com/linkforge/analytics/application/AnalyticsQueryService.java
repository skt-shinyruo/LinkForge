package com.linkforge.analytics.application;

import com.linkforge.foundation.security.TenantGuard;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AnalyticsQueryService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantGuard tenantGuard;

    private static final String TOP_LINKS_SQL_ORDER_BY_PV = """
            SELECT sl.id AS link_id, sl.code AS code, sl.original_url AS original_url,
                   SUM(s.pv) AS pv, SUM(s.uv) AS uv
            FROM link_stats_daily s
            JOIN short_links sl
              ON sl.id = s.link_id
             AND sl.tenant_id = s.tenant_id
            WHERE s.tenant_id = ?
              AND s.day >= ?
              AND s.day <= ?
            GROUP BY sl.id, sl.code, sl.original_url
            ORDER BY pv DESC, uv DESC, sl.id ASC
            LIMIT ?
            """;

    private static final String TOP_LINKS_SQL_ORDER_BY_UV = """
            SELECT sl.id AS link_id, sl.code AS code, sl.original_url AS original_url,
                   SUM(s.pv) AS pv, SUM(s.uv) AS uv
            FROM link_stats_daily s
            JOIN short_links sl
              ON sl.id = s.link_id
             AND sl.tenant_id = s.tenant_id
            WHERE s.tenant_id = ?
              AND s.day >= ?
              AND s.day <= ?
            GROUP BY sl.id, sl.code, sl.original_url
            ORDER BY uv DESC, pv DESC, sl.id ASC
            LIMIT ?
            """;

    public AnalyticsQueryService(JdbcTemplate jdbcTemplate, TenantGuard tenantGuard) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantGuard = tenantGuard;
    }

    public List<DailyStat> linkDaily(long tenantId, long linkId, LocalDate from, LocalDate to) {
        tenantGuard.requireCurrentTenant(tenantId);
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
                (rs, rowNum) -> new DailyStat(
                        rs.getDate("day").toLocalDate(),
                        rs.getLong("pv"),
                        rs.getLong("uv")
                ),
                tenantId, linkId, Date.valueOf(from), Date.valueOf(to)
        );
    }

    public List<DailyStat> tenantDaily(long tenantId, LocalDate from, LocalDate to) {
        tenantGuard.requireCurrentTenant(tenantId);
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
                (rs, rowNum) -> new DailyStat(
                        rs.getDate("day").toLocalDate(),
                        rs.getLong("pv"),
                        rs.getLong("uv")
                ),
                tenantId, Date.valueOf(from), Date.valueOf(to)
        );
    }

    public List<TopLinkStat> topLinks(long tenantId, LocalDate from, LocalDate to, int limit) {
        tenantGuard.requireCurrentTenant(tenantId);
        return topLinks(tenantId, from, to, limit, TopSortBy.PV);
    }

    public List<TopLinkStat> topLinks(long tenantId, LocalDate from, LocalDate to, int limit, TopSortBy sortBy) {
        tenantGuard.requireCurrentTenant(tenantId);
        TopSortBy s = (sortBy == null ? TopSortBy.PV : sortBy);
        String sql = (s == TopSortBy.UV ? TOP_LINKS_SQL_ORDER_BY_UV : TOP_LINKS_SQL_ORDER_BY_PV);
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new TopLinkStat(
                        rs.getLong("link_id"),
                        rs.getString("code"),
                        rs.getString("original_url"),
                        rs.getLong("pv"),
                        rs.getLong("uv")
                ),
                tenantId, Date.valueOf(from), Date.valueOf(to), limit
        );
    }

    public List<DimensionStat> linkDimensions(
            long tenantId,
            long linkId,
            LocalDate from,
            LocalDate to,
            String dimType,
            int limit
    ) {
        tenantGuard.requireCurrentTenant(tenantId);
        String t = normalizeDimType(dimType);

        Long totalPv = jdbcTemplate.queryForObject(
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
                tenantId, linkId, Date.valueOf(from), Date.valueOf(to), t
        );
        long total = totalPv == null ? 0L : totalPv;

        record Row(String value, long pv, long uv) {
        }

        List<Row> rows = jdbcTemplate.query(
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
                (rs, rowNum) -> new Row(
                        rs.getString("dim_value"),
                        rs.getLong("pv"),
                        rs.getLong("uv")
                ),
                tenantId, linkId, Date.valueOf(from), Date.valueOf(to), t, limit
        );

        return rows.stream().map(r -> new DimensionStat(
                r.value(),
                r.pv(),
                r.uv(),
                total <= 0 ? 0.0 : (r.pv() * 1.0 / total)
        )).toList();
    }

    public List<VisitEvent> linkEvents(long tenantId, long linkId, LocalDateTime from, LocalDateTime to, int limit) {
        tenantGuard.requireCurrentTenant(tenantId);
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
                (rs, rowNum) -> new VisitEvent(
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
                tenantId,
                linkId,
                from,
                to,
                limit
        );
    }

    public record DailyStat(LocalDate day, long pv, long uv) {
    }

    public enum TopSortBy {
        PV,
        UV
    }

    public record TopLinkStat(long linkId, String code, String originalUrl, long pv, long uv) {
    }

    public record DimensionStat(String value, long pv, long uv, double ratio) {
    }

    public record VisitEvent(
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

    private static String normalizeDimType(String dimType) {
        if (dimType == null || dimType.isBlank()) {
            return "unknown";
        }
        String t = dimType.trim().toLowerCase();
        return t.isBlank() ? "unknown" : t;
    }
}
