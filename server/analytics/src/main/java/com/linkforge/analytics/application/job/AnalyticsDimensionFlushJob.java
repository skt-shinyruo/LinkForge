package com.linkforge.analytics.application.job;

import com.linkforge.contract.analytics.AnalyticsKeys;
import com.linkforge.foundation.config.AppProperties;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 维度按天聚合落库作业：将 Redis 维度 PV（Hash）聚合写入 MySQL。
 *
 * <p>约束：以 active-set 为入口增量处理，避免全量扫描 keyspace。</p>
 */
@Component
public class AnalyticsDimensionFlushJob {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsDimensionFlushJob.class);

    private static final List<String> DEFAULT_DIM_TYPES = List.of(
            "referer_domain",
            "language",
            "ua_family",
            "os_family",
            "device_type",
            "utm_source",
            "utm_medium",
            "utm_campaign"
    );

    private final StringRedisTemplate redis;
    private final JdbcTemplate jdbcTemplate;
    private final AppProperties properties;

    public AnalyticsDimensionFlushJob(StringRedisTemplate redis, JdbcTemplate jdbcTemplate, AppProperties properties) {
        this.redis = redis;
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${APP_ANALYTICS_DIM_FLUSH_DELAY_MS:60000}")
    @SchedulerLock(name = "lf:job:analytics:dim-flush", lockAtMostFor = "PT15M")
    public void flush() {
        AppProperties.Analytics.Dimensions cfg = properties == null || properties.getAnalytics() == null
                ? null
                : properties.getAnalytics().getDimensions();
        if (cfg == null || !cfg.isEnabled()) {
            return;
        }

        LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
        int backfillDays = resolveBackfillDays();
        for (int i = 0; i < backfillDays; i++) {
            flushDay(todayUtc.minusDays(i), cfg);
        }
    }

    private void flushDay(LocalDate day, AppProperties.Analytics.Dimensions cfg) {
        String activeKey = AnalyticsKeys.activeSetKey(day);
        if (!hasKey(activeKey)) {
            return;
        }
        ScanOptions options = ScanOptions.scanOptions().count(1000).build();

        int maxLinksPerDay = cfg.getMaxLinksPerDay();
        int processed = 0;
        int batchSize = 500;

        List<String> batchMembers = new ArrayList<>(500);
        try (Cursor<String> cursor = redis.opsForSet().scan(activeKey, options)) {
            while (cursor.hasNext()) {
                if (maxLinksPerDay > 0 && processed >= maxLinksPerDay) {
                    break;
                }
                batchMembers.add(cursor.next());
                int currentBatchLimit = maxLinksPerDay > 0
                        ? Math.min(batchSize, maxLinksPerDay - processed)
                        : batchSize;
                if (batchMembers.size() >= currentBatchLimit) {
                    flushActiveMembers(day, cfg, batchMembers);
                    processed += batchMembers.size();
                    batchMembers.clear();
                }
            }
        } catch (Exception e) {
            log.debug("scan dim active set failed: activeKey={}, err={}", activeKey, e.getMessage());
            return;
        }

        if (!batchMembers.isEmpty()) {
            flushActiveMembers(day, cfg, batchMembers);
            processed += batchMembers.size();
        }

        if (maxLinksPerDay > 0 && processed >= maxLinksPerDay) {
            log.info("dim flush limited: day={}, processedLinks={}, maxLinksPerDay={}", day, processed, maxLinksPerDay);
        }
    }

    private int resolveBackfillDays() {
        AppProperties.Analytics a = properties == null ? null : properties.getAnalytics();
        int days = a == null ? 2 : a.getFlushBackfillDays();
        if (days <= 0) {
            days = 1;
        }
        long ttlDays = a == null ? 0 : a.getRedisKeyTtlDays();
        if (ttlDays > 0 && days > ttlDays) {
            days = (int) ttlDays;
        }
        return Math.max(days, 1);
    }

    private boolean hasKey(String key) {
        try {
            Boolean exists = redis.hasKey(key);
            return exists != null && exists;
        } catch (Exception e) {
            return false;
        }
    }

    private void flushActiveMembers(LocalDate day, AppProperties.Analytics.Dimensions cfg, List<String> members) {
        long startNs = System.nanoTime();
        List<MemberParts> parts = new ArrayList<>(members.size());
        for (String m : members) {
            MemberParts p = parseActiveMember(m);
            if (p != null) {
                parts.add(p);
            }
        }
        if (parts.isEmpty()) {
            return;
        }

        List<String> types = cfg.getTypes();
        if (types == null || types.isEmpty()) {
            types = DEFAULT_DIM_TYPES;
        }

        String sql = """
                INSERT INTO link_stats_dim_daily (tenant_id, link_id, day, dim_type, dim_value, pv, uv, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
                ON DUPLICATE KEY UPDATE pv = VALUES(pv), uv = VALUES(uv), updated_at = NOW()
                """;

        List<Object[]> batch = new ArrayList<>(800);
        long flushedRows = 0;
        ScanOptions hscan = ScanOptions.scanOptions().count(1000).build();

        for (MemberParts p : parts) {
            for (String rawType : types) {
                String dimType = rawType == null ? null : rawType.trim().toLowerCase();
                if (dimType == null || dimType.isBlank()) {
                    continue;
                }
                String key = AnalyticsKeys.dimPvHashKey(p.tenantId, p.linkId, day, dimType);

                try (Cursor<Map.Entry<Object, Object>> cursor = redis.opsForHash().scan(key, hscan)) {
                    while (cursor.hasNext()) {
                        Map.Entry<Object, Object> e = cursor.next();
                        String dimValue = e.getKey() == null ? null : String.valueOf(e.getKey());
                        if (dimValue == null || dimValue.isBlank()) {
                            continue;
                        }
                        long pv = safeLong(e.getValue(), 0L);
                        if (pv <= 0) {
                            continue;
                        }
                        batch.add(new Object[]{
                                p.tenantId,
                                p.linkId,
                                Date.valueOf(day),
                                dimType,
                                dimValue,
                                pv,
                                0L
                        });
                        if (batch.size() >= 500) {
                            flushedRows += batch.size();
                            flushBatch(sql, batch);
                            batch.clear();
                        }
                    }
                } catch (Exception ex) {
                    log.debug("scan dim hash failed: key={}, err={}", key, ex.getMessage());
                }
            }
        }

        if (!batch.isEmpty()) {
            flushedRows += batch.size();
            flushBatch(sql, batch);
        }

        if (flushedRows > 0) {
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
            log.info("flush dim batch ok: day={}, links={}, rows={}, latencyMs={}", day, parts.size(), flushedRows, latencyMs);
        }
    }

    private void flushBatch(String sql, List<Object[]> batch) {
        try {
            jdbcTemplate.batchUpdate(sql, batch);
        } catch (DataAccessException e) {
            log.warn("flush dim batch failed: size={}, err={}", batch.size(), e.getMessage());
        }
    }

    private static long safeLong(Object raw, long defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        if (raw instanceof Long l) {
            return l;
        }
        String s = String.valueOf(raw);
        if (s.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static MemberParts parseActiveMember(String member) {
        // {tenantId}:{linkId}
        if (member == null || member.isBlank()) {
            return null;
        }
        String[] parts = member.split(":", 2);
        if (parts.length != 2) {
            return null;
        }
        try {
            long tenantId = Long.parseLong(parts[0]);
            long linkId = Long.parseLong(parts[1]);
            return new MemberParts(tenantId, linkId);
        } catch (Exception e) {
            return null;
        }
    }

    private record MemberParts(long tenantId, long linkId) {
    }
}
