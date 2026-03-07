package com.linkforge.analytics.application.job;

import com.linkforge.contract.analytics.AnalyticsKeys;
import com.linkforge.foundation.config.AppProperties;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Component
public class AnalyticsFlushJob {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsFlushJob.class);

    private final StringRedisTemplate redis;
    private final JdbcTemplate jdbcTemplate;
    private final AppProperties properties;

    public AnalyticsFlushJob(StringRedisTemplate redis, JdbcTemplate jdbcTemplate, AppProperties properties) {
        this.redis = redis;
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${APP_ANALYTICS_FLUSH_DELAY_MS:60000}")
    @SchedulerLock(name = "lf:job:analytics:flush", lockAtMostFor = "PT10M")
    public void flush() {
        // 约定：Redirect 侧会把“被触达的 linkId”写入当日活跃集合，flush 只需增量读取，不再做全量 SCAN
        // 为覆盖跨天边界与短暂停摆，支持按回补窗口（最近 N 天）追赶 flush
        LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
        int backfillDays = resolveBackfillDays();
        for (int i = 0; i < backfillDays; i++) {
            flushDay(todayUtc.minusDays(i));
        }
    }

    private void flushDay(LocalDate day) {
        String activeKey = AnalyticsKeys.activeSetKey(day);
        if (!hasKey(activeKey)) {
            return;
        }
        ScanOptions options = ScanOptions.scanOptions().count(1000).build();

        List<String> batchMembers = new ArrayList<>(500);
        try (Cursor<String> cursor = redis.opsForSet().scan(activeKey, options)) {
            while (cursor.hasNext()) {
                batchMembers.add(cursor.next());
                if (batchMembers.size() >= 500) {
                    flushActiveMembers(day, batchMembers);
                    batchMembers.clear();
                }
            }
        } catch (Exception e) {
            log.debug("scan active set failed: activeKey={}, err={}", activeKey, e.getMessage());
            return;
        }

        if (!batchMembers.isEmpty()) {
            flushActiveMembers(day, batchMembers);
        }
    }

    private int resolveBackfillDays() {
        AppProperties.Analytics cfg = properties == null ? null : properties.getAnalytics();
        int days = cfg == null ? 2 : cfg.getFlushBackfillDays();
        if (days <= 0) {
            days = 1;
        }
        long ttlDays = cfg == null ? 0 : cfg.getRedisKeyTtlDays();
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

    void flushActiveMembers(LocalDate day, List<String> members) {
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

        List<String> pvKeys = parts.stream().map(p -> AnalyticsKeys.pvKey(p.tenantId, p.linkId, day)).toList();
        List<String> pvValues = redis.opsForValue().multiGet(pvKeys);

        List<String> uvKeys = parts.stream().map(p -> AnalyticsKeys.uvKey(p.tenantId, p.linkId, day)).toList();
        List<Long> uvValues = pfCountPipeline(uvKeys);

        String sql = """
                INSERT INTO link_stats_daily (link_id, tenant_id, day, pv, uv, updated_at)
                VALUES (?, ?, ?, ?, ?, NOW())
                ON DUPLICATE KEY UPDATE pv = VALUES(pv), uv = VALUES(uv), updated_at = NOW()
                """;

        List<Object[]> batch = new ArrayList<>(parts.size());
        int skipped = 0;
        for (int i = 0; i < parts.size(); i++) {
            MemberParts p = parts.get(i);
            String pvRaw = pvValues == null || i >= pvValues.size() ? null : pvValues.get(i);
            Long uvRaw = uvValues == null || i >= uvValues.size() ? null : uvValues.get(i);

            // flush 侧不允许“缺失 key -> 兜底 0 -> 覆盖写库”导致的统计回退/写回 0 数据破坏
            long pv = safeLong(pvRaw, -1L);
            long uv = safeLong(uvRaw, -1L);
            if (pv <= 0 || uv <= 0) {
                skipped++;
                continue;
            }
            batch.add(new Object[]{p.linkId, p.tenantId, Date.valueOf(day), pv, uv});
        }

        if (batch.isEmpty()) {
            if (skipped > 0) {
                log.info("flush stats batch skipped: day={}, scanned={}, skipped={}", day, parts.size(), skipped);
            }
            return;
        }

        try {
            jdbcTemplate.batchUpdate(sql, batch);
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
            log.info(
                    "flush stats batch ok: day={}, written={}, scanned={}, skipped={}, latencyMs={}",
                    day,
                    batch.size(),
                    parts.size(),
                    skipped,
                    latencyMs
            );
        } catch (DataAccessException e) {
            log.warn(
                    "flush stats batch failed: day={}, written={}, scanned={}, skipped={}, err={}",
                    day,
                    batch.size(),
                    parts.size(),
                    skipped,
                    e.getMessage()
            );
        }
    }

    private List<Long> pfCountPipeline(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        RedisSerializer<String> serializer = redis.getStringSerializer();
        byte[] dummyKey = serializer.serialize("stats:__dummy__:never");
        List<Object> raw = redis.executePipelined((RedisCallback<Object>) connection -> {
            for (String k : keys) {
                byte[] kk = (k == null || k.isBlank()) ? null : serializer.serialize(k);
                if (kk == null || kk.length == 0) {
                    // 保持 pipeline 结果与 key 列表对齐：用一个固定的“不存在 key”占位
                    connection.pfCount(dummyKey);
                } else {
                    connection.pfCount(kk);
                }
            }
            return null;
        });
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<Long> out = new ArrayList<>(raw.size());
        for (Object o : raw) {
            out.add(o instanceof Long l ? l : null);
        }
        return out;
    }

    private static long safeLong(String raw, long defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static long safeLong(Long raw, long defaultValue) {
        return raw == null ? defaultValue : raw;
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
