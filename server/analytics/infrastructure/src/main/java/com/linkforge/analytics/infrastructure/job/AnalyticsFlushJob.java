package com.linkforge.analytics.infrastructure.job;

import com.linkforge.analytics.infrastructure.persistence.mapper.AnalyticsScopeStatsDailyMapper;
import com.linkforge.analytics.infrastructure.persistence.mapper.AnalyticsScopeStatsDailyUpsertRow;
import com.linkforge.analytics.infrastructure.persistence.mapper.LinkStatsDailyMapper;
import com.linkforge.analytics.infrastructure.persistence.mapper.LinkStatsDailyUpsertRow;
import com.linkforge.contract.analytics.AnalyticsKeys;
import com.linkforge.foundation.config.AnalyticsProperties;
import com.linkforge.foundation.observability.OperationalMetrics;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Flushes the current Redis PV/UV snapshots selected by V2 generation markers. */
@Component
public class AnalyticsFlushJob {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsFlushJob.class);
    private static final int BATCH_SIZE = 500;
    private static final int MAX_MARKER_BATCHES_PER_DAY = 10;

    private final StringRedisTemplate redis;
    private final LinkStatsDailyMapper linkStatsDailyMapper;
    private final AnalyticsScopeStatsDailyMapper scopeStatsDailyMapper;
    private final AnalyticsProperties analyticsProperties;
    private final VersionedDirtyMarkerStore markerStore;
    private final OperationalMetrics metrics;

    public AnalyticsFlushJob(
            StringRedisTemplate redis,
            LinkStatsDailyMapper linkStatsDailyMapper,
            AnalyticsProperties analyticsProperties
    ) {
        this(redis, linkStatsDailyMapper, null, analyticsProperties);
    }

    public AnalyticsFlushJob(
            StringRedisTemplate redis,
            LinkStatsDailyMapper linkStatsDailyMapper,
            AnalyticsScopeStatsDailyMapper scopeStatsDailyMapper,
            AnalyticsProperties analyticsProperties
    ) {
        this(redis, linkStatsDailyMapper, scopeStatsDailyMapper, analyticsProperties, OperationalMetrics.noop());
    }

    @Autowired
    public AnalyticsFlushJob(
            StringRedisTemplate redis,
            LinkStatsDailyMapper linkStatsDailyMapper,
            AnalyticsScopeStatsDailyMapper scopeStatsDailyMapper,
            AnalyticsProperties analyticsProperties,
            OperationalMetrics metrics
    ) {
        this.redis = redis;
        this.linkStatsDailyMapper = linkStatsDailyMapper;
        this.scopeStatsDailyMapper = scopeStatsDailyMapper;
        this.analyticsProperties = analyticsProperties;
        this.markerStore = new VersionedDirtyMarkerStore(redis);
        this.metrics = metrics == null ? OperationalMetrics.noop() : metrics;
    }

    @Scheduled(fixedDelayString = "${APP_ANALYTICS_FLUSH_DELAY_MS:60000}")
    @SchedulerLock(name = "lf:job:analytics:flush", lockAtMostFor = "PT10M")
    public void flush() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (int i = 0; i < resolveBackfillDays(); i++) {
            LocalDate day = today.minusDays(i);
            flushLinkDay(day);
            if (scopeStatsDailyMapper != null) {
                flushScopeDay(day);
            }
        }
    }

    private void flushLinkDay(LocalDate day) {
        String marker = AnalyticsKeys.statsDirtyMarkerV2Key(day);
        String firstSeen = AnalyticsKeys.statsDirtyMarkerV2FirstSeenKey(day);
        for (int i = 0; i < MAX_MARKER_BATCHES_PER_DAY; i++) {
            List<VersionedDirtyMarkerStore.Claim> claims = claim(marker, firstSeen, "link");
            if (claims.isEmpty()) {
                return;
            }
            if (!flushDirtyMembers(day, claims.stream().map(VersionedDirtyMarkerStore.Claim::member).toList())) {
                return;
            }
            if (!complete(marker, firstSeen, "link", claims)) {
                return;
            }
        }
    }

    private void flushScopeDay(LocalDate day) {
        String marker = AnalyticsKeys.scopeDirtyMarkerV2Key(day);
        String firstSeen = AnalyticsKeys.scopeDirtyMarkerV2FirstSeenKey(day);
        for (int i = 0; i < MAX_MARKER_BATCHES_PER_DAY; i++) {
            List<VersionedDirtyMarkerStore.Claim> claims = claim(marker, firstSeen, "scope");
            if (claims.isEmpty()) {
                return;
            }
            if (!flushDirtyScopeMembers(day, claims.stream().map(VersionedDirtyMarkerStore.Claim::member).toList())) {
                return;
            }
            if (!complete(marker, firstSeen, "scope", claims)) {
                return;
            }
        }
    }

    private List<VersionedDirtyMarkerStore.Claim> claim(String marker, String firstSeen, String kind) {
        try {
            Long size = redis.opsForHash().size(marker);
            metrics.set("linkforge.analytics.dirty.marker.cardinality",
                    size == null ? 0L : Math.max(size, 0L), "marker", kind);
            List<VersionedDirtyMarkerStore.Claim> claims = markerStore.claim(marker, firstSeen, BATCH_SIZE);
            long oldest = claims.stream()
                    .mapToLong(VersionedDirtyMarkerStore.Claim::firstSeenEpochMillis)
                    .filter(value -> value > 0L)
                    .min()
                    .orElse(0L);
            metrics.set("linkforge.analytics.dirty.marker.oldest_age_millis",
                    oldest == 0L ? 0L : Math.max(System.currentTimeMillis() - oldest, 0L), "marker", kind);
            return claims;
        } catch (RuntimeException failure) {
            metrics.increment("linkforge.job.failures", "job", "analytics_stats_flush", "stage", "marker_claim");
            log.debug("claim analytics marker failed: kind={}, err={}", kind, failure.getMessage());
            return List.of();
        }
    }

    private boolean complete(
            String marker,
            String firstSeen,
            String kind,
            List<VersionedDirtyMarkerStore.Claim> claims
    ) {
        try {
            VersionedDirtyMarkerStore.Completion result = markerStore.complete(marker, firstSeen, claims);
            metrics.add("linkforge.analytics.dirty.marker.completed", result.completed(), "marker", kind);
            metrics.add("linkforge.analytics.dirty.marker.generation_conflicts",
                    result.generationConflicts(), "marker", kind);
            return true;
        } catch (RuntimeException failure) {
            metrics.increment("linkforge.job.failures", "job", "analytics_stats_flush", "stage", "marker_complete");
            log.debug("complete analytics marker failed: kind={}, err={}", kind, failure.getMessage());
            return false;
        }
    }

    /** Writes current link snapshots. Kept package-private for focused unit tests. */
    boolean flushDirtyMembers(LocalDate day, List<String> members) {
        if (members == null || members.isEmpty()) {
            return true;
        }
        long startedAt = System.nanoTime();
        Map<MemberParts, MemberParts> unique = new LinkedHashMap<>();
        for (String member : members) {
            MemberParts parsed = parseLinkMember(member);
            if (parsed != null) {
                unique.putIfAbsent(parsed, parsed);
            }
        }
        List<MemberParts> parts = new ArrayList<>(unique.values());
        if (parts.isEmpty()) {
            return true;
        }

        List<String> pvValues = redis.opsForValue().multiGet(
                parts.stream().map(p -> AnalyticsKeys.pvKey(p.tenantId, p.linkId, day)).toList());
        List<Long> uvValues = pfCountPipeline(
                parts.stream().map(p -> AnalyticsKeys.uvKey(p.tenantId, p.linkId, day)).toList());

        List<LinkStatsDailyUpsertRow> rows = new ArrayList<>(parts.size());
        for (int i = 0; i < parts.size(); i++) {
            long pv = safeLong(pvValues == null || i >= pvValues.size() ? null : pvValues.get(i), -1L);
            if (pv <= 0L) {
                continue;
            }
            LinkStatsDailyUpsertRow row = new LinkStatsDailyUpsertRow();
            row.setTenantId(parts.get(i).tenantId);
            row.setLinkId(parts.get(i).linkId);
            row.setDay(day);
            row.setPv(pv);
            row.setUv(Math.max(safeLong(uvValues == null || i >= uvValues.size() ? null : uvValues.get(i), 0L), 0L));
            rows.add(row);
        }
        if (rows.isEmpty()) {
            return true;
        }

        try {
            linkStatsDailyMapper.batchUpsert(rows);
            metrics.add("linkforge.job.rows", rows.size(), "job", "analytics_stats_flush", "result", "success");
            metrics.record("linkforge.job.db_batch", Duration.ofNanos(System.nanoTime() - startedAt),
                    "job", "analytics_stats_flush", "result", "success");
            return true;
        } catch (DataAccessException failure) {
            metrics.increment("linkforge.job.failures", "job", "analytics_stats_flush", "stage", "database");
            log.warn("flush analytics link stats failed: day={}, rows={}, err={}", day, rows.size(), failure.getMessage());
            return false;
        }
    }

    /** Writes current tenant/application/domain UV snapshots. */
    boolean flushDirtyScopeMembers(LocalDate day, List<String> members) {
        if (scopeStatsDailyMapper == null || members == null || members.isEmpty()) {
            return true;
        }
        long startedAt = System.nanoTime();
        Map<String, ScopeMemberParts> unique = new LinkedHashMap<>();
        for (String member : members) {
            ScopeMemberParts parsed = parseScopeMember(member);
            if (parsed != null) {
                unique.putIfAbsent(parsed.key(), parsed);
            }
        }
        List<ScopeMemberParts> parts = new ArrayList<>(unique.values());
        if (parts.isEmpty()) {
            return true;
        }

        List<Long> uvValues = pfCountPipeline(parts.stream().map(p -> scopeUvKey(p, day)).toList());
        List<AnalyticsScopeStatsDailyUpsertRow> rows = new ArrayList<>(parts.size());
        for (int i = 0; i < parts.size(); i++) {
            AnalyticsScopeStatsDailyUpsertRow row = new AnalyticsScopeStatsDailyUpsertRow();
            ScopeMemberParts part = parts.get(i);
            row.setTenantId(part.tenantId);
            row.setScopeType(part.scopeType);
            row.setScopeId(part.scopeId);
            row.setDay(day);
            row.setUv(Math.max(safeLong(uvValues == null || i >= uvValues.size() ? null : uvValues.get(i), 0L), 0L));
            rows.add(row);
        }
        try {
            scopeStatsDailyMapper.batchUpsert(rows);
            metrics.add("linkforge.job.rows", rows.size(), "job", "analytics_scope_flush", "result", "success");
            metrics.record("linkforge.job.db_batch", Duration.ofNanos(System.nanoTime() - startedAt),
                    "job", "analytics_scope_flush", "result", "success");
            return true;
        } catch (DataAccessException failure) {
            metrics.increment("linkforge.job.failures", "job", "analytics_scope_flush", "stage", "database");
            log.warn("flush analytics scope stats failed: day={}, rows={}, err={}", day, rows.size(), failure.getMessage());
            return false;
        }
    }

    private List<Long> pfCountPipeline(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        RedisSerializer<String> serializer = redis.getStringSerializer();
        byte[] dummy = serializer.serialize("stats:__dummy__:never");
        List<Object> raw = redis.executePipelined((RedisCallback<Object>) connection -> {
            for (String key : keys) {
                byte[] encoded = key == null || key.isBlank() ? null : serializer.serialize(key);
                connection.pfCount(encoded == null ? dummy : encoded);
            }
            return null;
        });
        if (raw == null) {
            return List.of();
        }
        return raw.stream().map(value -> value instanceof Long l ? l : null).toList();
    }

    private int resolveBackfillDays() {
        int configured = analyticsProperties == null ? 1 : analyticsProperties.getFlushBackfillDays();
        int days = Math.max(configured, 1);
        long ttl = analyticsProperties == null ? 0L : analyticsProperties.getRedisKeyTtlDays();
        return ttl > 0L ? Math.min(days, (int) Math.min(ttl, Integer.MAX_VALUE)) : days;
    }

    private static MemberParts parseLinkMember(String member) {
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
            return tenantId > 0L && linkId > 0L ? new MemberParts(tenantId, linkId) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static ScopeMemberParts parseScopeMember(String member) {
        if (member == null || member.isBlank()) {
            return null;
        }
        String[] parts = member.split(":", 3);
        if (parts.length != 3) {
            return null;
        }
        String type = parts[0].trim().toLowerCase();
        if (!type.equals("tenant") && !type.equals("application") && !type.equals("domain")) {
            return null;
        }
        try {
            long tenantId = Long.parseLong(parts[1]);
            long scopeId = Long.parseLong(parts[2]);
            if (tenantId <= 0L || (!type.equals("tenant") && scopeId <= 0L)) {
                return null;
            }
            return new ScopeMemberParts(type, tenantId, type.equals("tenant") ? 0L : scopeId);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String scopeUvKey(ScopeMemberParts part, LocalDate day) {
        return switch (part.scopeType) {
            case "tenant" -> AnalyticsKeys.tenantScopeUvKey(part.tenantId, day);
            case "application" -> AnalyticsKeys.applicationScopeUvKey(part.tenantId, part.scopeId, day);
            case "domain" -> AnalyticsKeys.domainScopeUvKey(part.tenantId, part.scopeId, day);
            default -> "stats:__dummy__:never";
        };
    }

    private static long safeLong(String value, long fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long safeLong(Long value, long fallback) {
        return value == null ? fallback : value;
    }

    private record MemberParts(long tenantId, long linkId) {
    }

    private record ScopeMemberParts(String scopeType, long tenantId, long scopeId) {
        private String key() {
            return scopeType + ":" + tenantId + ":" + scopeId;
        }
    }
}
