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
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 Redis 中的链接和范围日聚合快照持久化到 MySQL。
 *
 * <p>当前刷新信号是以 {@code tenantId:linkId} 为 field 的 V2 generation marker。作业读取 Redis 当前 PV 与
 * HyperLogLog 估算的 UV 后批量写库，并只在 generation 未变化时删除 marker；写库期间的新访问会推进
 * generation 并保留下一轮刷新。兼容期开启时还会排空 legacy dirty Stream。SQL 使用 {@code GREATEST}
 * 保持日表单调递增，因此重放不会回退已落库数据。</p>
 *
 * <p>数据库或 Redis 读取失败时不 ACK 当前批次，使 consumer group pending 负责重试。ACK 失败仍可能
 * 造成后续重放，故这里不宣称 exactly-once。统计日和回填窗口均按 UTC 计算，且窗口被 Redis key TTL
 * 上限截断，过期 key 无法被该作业补算。</p>
 *
 * <p>legacy Stream 的 {@code XLEN} 仅作为 retained entries；实际剩余量按全部回填日期的 group lag 与
 * pending 汇总，并在 job 层合并 link 与 scope。排空计数只使用 Redis 实际 ACK 数。</p>
 */
@Component
public class AnalyticsFlushJob {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsFlushJob.class);
    private static final String GROUP = "lf-stats-flush";
    private static final String CONSUMER = "lf-stats-flush-consumer";
    private static final int BATCH_SIZE = 500;
    private static final int MAX_MARKER_BATCHES_PER_DAY = 10;

    private final StringRedisTemplate redis;
    private final LinkStatsDailyMapper linkStatsDailyMapper;
    private final AnalyticsScopeStatsDailyMapper scopeStatsDailyMapper;
    private final AnalyticsProperties analyticsProperties;
    private final RedisStreamBatchConsumer streamConsumer;
    private final LegacyDirtyStreamMetrics legacyStreamMetrics;
    private final VersionedDirtyMarkerStore markerStore;
    private final OperationalMetrics metrics;

    public AnalyticsFlushJob(StringRedisTemplate redis, LinkStatsDailyMapper linkStatsDailyMapper, AnalyticsProperties analyticsProperties) {
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
        this.metrics = metrics == null ? OperationalMetrics.noop() : metrics;
        this.streamConsumer = new RedisStreamBatchConsumer(
                redis, GROUP, CONSUMER, "analytics_stats_flush", this.metrics, false);
        this.legacyStreamMetrics = new LegacyDirtyStreamMetrics(redis, streamConsumer, this.metrics);
        this.markerStore = new VersionedDirtyMarkerStore(redis);
    }

    /**
     * 依次处理 UTC 当日及有限回填窗口内的链接、范围 V2 marker，并按配置兼容读取 legacy Streams。
     *
     * <p>ShedLock 只降低多实例重复调度概率；幂等性仍依赖 Redis 快照和 MySQL 的单调 upsert。</p>
     */
    @Scheduled(fixedDelayString = "${APP_ANALYTICS_FLUSH_DELAY_MS:60000}")
    @SchedulerLock(name = "lf:job:analytics:flush", lockAtMostFor = "PT10M")
    public void flush() {
        LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
        int backfillDays = resolveBackfillDays();
        LegacyDirtyStreamMetrics.Aggregate linkLegacy = legacyReadEnabled()
                ? legacyStreamMetrics.start("link") : null;
        LegacyDirtyStreamMetrics.Aggregate scopeLegacy = legacyReadEnabled() && scopeStatsDailyMapper != null
                ? legacyStreamMetrics.start("scope") : null;
        for (int i = 0; i < backfillDays; i++) {
            flushDay(todayUtc.minusDays(i), linkLegacy, scopeLegacy);
        }
        RedisStreamBatchConsumer.StreamState legacyState = null;
        if (linkLegacy != null) {
            legacyState = linkLegacy.publish();
        }
        if (scopeLegacy != null) {
            legacyState = RedisStreamBatchConsumer.StreamState.combine(legacyState, scopeLegacy.publish());
        }
        if (legacyState != null) {
            streamConsumer.publishStreamState(legacyState);
        }
    }

    private void flushDay(
            LocalDate day,
            LegacyDirtyStreamMetrics.Aggregate linkLegacy,
            LegacyDirtyStreamMetrics.Aggregate scopeLegacy
    ) {
        flushLinkStatsDay(day, linkLegacy);
        flushScopeStatsDay(day, scopeLegacy);
    }

    private void flushLinkStatsDay(LocalDate day, LegacyDirtyStreamMetrics.Aggregate legacy) {
        flushVersionedLinkStatsDay(day);
        if (!legacyReadEnabled()) {
            return;
        }
        String streamKey = AnalyticsKeys.statsDirtyStreamKey(day);
        try {
            if (!streamConsumer.ensureGroup(streamKey)) {
                return;
            }

            while (true) {
                List<MapRecord<String, Object, Object>> records = readNext(streamKey);
                if (records == null || records.isEmpty()) {
                    return;
                }
                List<String> members = extractMembers(records);
                if (!members.isEmpty() && !flushDirtyMembers(day, members)) {
                    return;
                }
                if (!acknowledgeLegacy(streamKey, records, "link")) {
                    return;
                }
            }
        } finally {
            legacy.observe(streamKey);
        }
    }

    private void flushScopeStatsDay(LocalDate day, LegacyDirtyStreamMetrics.Aggregate legacy) {
        if (scopeStatsDailyMapper == null) {
            return;
        }
        flushVersionedScopeStatsDay(day);
        if (!legacyReadEnabled()) {
            return;
        }
        String streamKey = AnalyticsKeys.scopeDirtyStreamKey(day);
        try {
            if (!streamConsumer.ensureGroup(streamKey)) {
                return;
            }

            while (true) {
                List<MapRecord<String, Object, Object>> records = readNext(streamKey);
                if (records == null || records.isEmpty()) {
                    return;
                }
                List<String> members = extractMembers(records);
                if (!members.isEmpty() && !flushDirtyScopeMembers(day, members)) {
                    return;
                }
                if (!acknowledgeLegacy(streamKey, records, "scope")) {
                    return;
                }
            }
        } finally {
            legacy.observe(streamKey);
        }
    }

    private void flushVersionedLinkStatsDay(LocalDate day) {
        String markerKey = AnalyticsKeys.statsDirtyMarkerV2Key(day);
        String firstSeenKey = AnalyticsKeys.statsDirtyMarkerV2FirstSeenKey(day);
        for (int batch = 0; batch < MAX_MARKER_BATCHES_PER_DAY; batch++) {
            List<VersionedDirtyMarkerStore.Claim> claims = claimMarkers(markerKey, firstSeenKey, "link");
            if (claims.isEmpty()) {
                return;
            }
            if (!flushDirtyMembers(day, claims.stream().map(VersionedDirtyMarkerStore.Claim::member).toList())) {
                return;
            }
            if (!completeMarkers(markerKey, firstSeenKey, "link", claims)) {
                return;
            }
        }
    }

    private void flushVersionedScopeStatsDay(LocalDate day) {
        String markerKey = AnalyticsKeys.scopeDirtyMarkerV2Key(day);
        String firstSeenKey = AnalyticsKeys.scopeDirtyMarkerV2FirstSeenKey(day);
        for (int batch = 0; batch < MAX_MARKER_BATCHES_PER_DAY; batch++) {
            List<VersionedDirtyMarkerStore.Claim> claims = claimMarkers(markerKey, firstSeenKey, "scope");
            if (claims.isEmpty()) {
                return;
            }
            if (!flushDirtyScopeMembers(day, claims.stream().map(VersionedDirtyMarkerStore.Claim::member).toList())) {
                return;
            }
            if (!completeMarkers(markerKey, firstSeenKey, "scope", claims)) {
                return;
            }
        }
    }

    private List<VersionedDirtyMarkerStore.Claim> claimMarkers(
            String markerKey,
            String firstSeenKey,
            String markerType
    ) {
        try {
            Long cardinality = redis.opsForHash().size(markerKey);
            metrics.set("linkforge.analytics.dirty.marker.cardinality",
                    cardinality == null ? 0L : Math.max(cardinality, 0L), "marker", markerType);
            List<VersionedDirtyMarkerStore.Claim> claims = markerStore.claim(markerKey, firstSeenKey, BATCH_SIZE);
            long oldest = claims.stream()
                    .mapToLong(VersionedDirtyMarkerStore.Claim::firstSeenEpochMillis)
                    .filter(value -> value > 0)
                    .min()
                    .orElse(0L);
            long age = oldest == 0L ? 0L : Math.max(System.currentTimeMillis() - oldest, 0L);
            metrics.set("linkforge.analytics.dirty.marker.oldest_age_millis", age, "marker", markerType);
            return claims;
        } catch (RuntimeException ex) {
            metrics.increment("linkforge.job.failures", "job", "analytics_stats_flush", "stage", "marker_claim");
            log.debug("claim versioned dirty markers failed: marker={}, err={}", markerType, ex.getMessage());
            return List.of();
        }
    }

    private boolean completeMarkers(
            String markerKey,
            String firstSeenKey,
            String markerType,
            List<VersionedDirtyMarkerStore.Claim> claims
    ) {
        try {
            VersionedDirtyMarkerStore.Completion completion = markerStore.complete(markerKey, firstSeenKey, claims);
            metrics.add("linkforge.analytics.dirty.marker.completed", completion.completed(), "marker", markerType);
            metrics.add("linkforge.analytics.dirty.marker.generation_conflicts",
                    completion.generationConflicts(), "marker", markerType);
            return true;
        } catch (RuntimeException ex) {
            metrics.increment("linkforge.job.failures", "job", "analytics_stats_flush", "stage", "marker_complete");
            log.debug("complete versioned dirty markers failed: marker={}, err={}", markerType, ex.getMessage());
            return false;
        }
    }

    private boolean legacyReadEnabled() {
        AnalyticsProperties.DirtyMarker cfg = analyticsProperties == null ? null : analyticsProperties.getDirtyMarker();
        return cfg == null || cfg.isLegacyReadEnabled();
    }

    private boolean acknowledgeLegacy(
            String streamKey,
            List<MapRecord<String, Object, Object>> records,
            String markerType
    ) {
        List<RecordId> ids = records.stream().map(MapRecord::getId).toList();
        long acknowledged = streamConsumer.acknowledge(streamKey, ids);
        reportLegacyDrained(markerType, acknowledged);
        return acknowledged == ids.size();
    }

    private void reportLegacyDrained(String markerType, long records) {
        if (records > 0) {
            metrics.add("linkforge.analytics.dirty.legacy.drained", records, "marker", markerType);
        }
    }

    private int resolveBackfillDays() {
        AnalyticsProperties cfg = analyticsProperties;
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

    private List<MapRecord<String, Object, Object>> readNext(String streamKey) {
        AnalyticsProperties.Events events = analyticsProperties == null ? null : analyticsProperties.getEvents();
        boolean reclaimEnabled = events == null || events.isPendingReclaimEnabled();
        long minIdleMs = events == null ? 60_000L : Math.max(events.getPendingReclaimMinIdleMs(), 0L);
        int reclaimCount = events == null ? BATCH_SIZE : Math.max(events.getPendingReclaimCount(), 1);
        return streamConsumer.readNext(
                streamKey,
                BATCH_SIZE,
                null,
                reclaimEnabled,
                Duration.ofMillis(minIdleMs),
                reclaimCount
        );
    }

    /**
     * 将 V2 marker 或 legacy Stream 指定的“链接-日期”成员当前累计值写入 MySQL。
     *
     * @return {@code true} 表示本批消息可以 ACK；数据库写入失败时返回 {@code false}，保留 pending
     *         消息供后续调度重试
     */
    boolean flushDirtyMembers(LocalDate day, List<String> members) {
        long startNs = System.nanoTime();
        Map<MemberParts, MemberParts> partsByKey = new LinkedHashMap<>();
        for (String m : members) {
            MemberParts p = parseDirtyLinkMember(m);
            if (p != null) {
                partsByKey.putIfAbsent(p, p);
            }
        }
        List<MemberParts> parts = new ArrayList<>(partsByKey.values());
        if (parts.isEmpty()) {
            return true;
        }

        List<String> pvKeys = parts.stream().map(p -> AnalyticsKeys.pvKey(p.tenantId, p.linkId, day)).toList();
        List<String> pvValues = redis.opsForValue().multiGet(pvKeys);

        List<String> uvKeys = parts.stream().map(p -> AnalyticsKeys.uvKey(p.tenantId, p.linkId, day)).toList();
        List<Long> uvValues = pfCountPipeline(uvKeys);

        List<LinkStatsDailyUpsertRow> batch = new ArrayList<>(parts.size());
        int skipped = 0;
        for (int i = 0; i < parts.size(); i++) {
            MemberParts p = parts.get(i);
            String pvRaw = pvValues == null || i >= pvValues.size() ? null : pvValues.get(i);
            Long uvRaw = uvValues == null || i >= uvValues.size() ? null : uvValues.get(i);

            long pv = safeLong(pvRaw, -1L);
            if (pv <= 0) {
                skipped++;
                continue;
            }
            long uv = safeLong(uvRaw, 0L);

            LinkStatsDailyUpsertRow row = new LinkStatsDailyUpsertRow();
            row.setLinkId(p.linkId);
            row.setTenantId(p.tenantId);
            row.setDay(day);
            row.setPv(pv);
            row.setUv(Math.max(uv, 0L));
            batch.add(row);
        }

        if (batch.isEmpty()) {
            if (skipped > 0) {
                log.info("flush stats batch skipped: day={}, scanned={}, skipped={}", day, parts.size(), skipped);
            }
            return true;
        }

        try {
            linkStatsDailyMapper.batchUpsert(batch);
            metrics.add("linkforge.job.rows", batch.size(), "job", "analytics_stats_flush", "result", "success");
            metrics.record("linkforge.job.db_batch", Duration.ofNanos(System.nanoTime() - startNs), "job", "analytics_stats_flush", "result", "success");
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
            log.info(
                    "flush stats batch ok: day={}, written={}, scanned={}, skipped={}, latencyMs={}",
                    day,
                    batch.size(),
                    parts.size(),
                    skipped,
                    latencyMs
            );
            return true;
        } catch (DataAccessException e) {
            metrics.increment("linkforge.job.failures", "job", "analytics_stats_flush", "stage", "database");
            metrics.record("linkforge.job.db_batch", Duration.ofNanos(System.nanoTime() - startNs), "job", "analytics_stats_flush", "result", "failure");
            log.warn(
                    "flush stats batch failed: day={}, written={}, scanned={}, skipped={}, err={}",
                    day,
                    batch.size(),
                    parts.size(),
                    skipped,
                    e.getMessage()
            );
            return false;
        }
    }

    /**
     * 将范围 V2 marker 或 legacy Stream 中的租户、应用和域成员写入范围 UV 日表。
     *
     * <p>范围成员格式分别为 {@code tenant:tenantId:0}、
     * {@code application:tenantId:applicationId} 和 {@code domain:tenantId:domainId}。同一批重复成员
     * 会去重；UV 同样来自 HLL，故为近似去重值。</p>
     *
     * @return {@code false} 时调用方不得 ACK，避免数据库失败丢失待刷新的成员
     */
    boolean flushDirtyScopeMembers(LocalDate day, List<String> members) {
        if (scopeStatsDailyMapper == null) {
            return true;
        }
        long startNs = System.nanoTime();
        Map<String, ScopeMemberParts> partsByKey = new LinkedHashMap<>();
        for (String m : members) {
            ScopeMemberParts p = parseScopeMember(m);
            if (p != null) {
                partsByKey.putIfAbsent(p.key(), p);
            }
        }
        List<ScopeMemberParts> parts = new ArrayList<>(partsByKey.values());
        if (parts.isEmpty()) {
            return true;
        }

        List<String> uvKeys = parts.stream().map(p -> scopeUvKey(p, day)).toList();
        List<Long> uvValues = pfCountPipeline(uvKeys);
        List<AnalyticsScopeStatsDailyUpsertRow> batch = new ArrayList<>(parts.size());

        for (int i = 0; i < parts.size(); i++) {
            ScopeMemberParts p = parts.get(i);
            Long uvRaw = uvValues == null || i >= uvValues.size() ? null : uvValues.get(i);
            long uv = safeLong(uvRaw, 0L);

            AnalyticsScopeStatsDailyUpsertRow row = new AnalyticsScopeStatsDailyUpsertRow();
            row.setTenantId(p.tenantId);
            row.setScopeType(p.scopeType);
            row.setScopeId(p.scopeId);
            row.setDay(day);
            row.setUv(Math.max(uv, 0L));
            batch.add(row);
        }

        if (batch.isEmpty()) {
            return true;
        }

        try {
            scopeStatsDailyMapper.batchUpsert(batch);
            metrics.add("linkforge.job.rows", batch.size(), "job", "analytics_scope_flush", "result", "success");
            metrics.record("linkforge.job.db_batch", Duration.ofNanos(System.nanoTime() - startNs), "job", "analytics_scope_flush", "result", "success");
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
            log.info("flush scope stats batch ok: day={}, written={}, latencyMs={}", day, batch.size(), latencyMs);
            return true;
        } catch (DataAccessException e) {
            metrics.increment("linkforge.job.failures", "job", "analytics_scope_flush", "stage", "database");
            metrics.record("linkforge.job.db_batch", Duration.ofNanos(System.nanoTime() - startNs), "job", "analytics_scope_flush", "result", "failure");
            log.warn("flush scope stats batch failed: day={}, written={}, err={}", day, batch.size(), e.getMessage());
            return false;
        }
    }

    private static String scopeUvKey(ScopeMemberParts p, LocalDate day) {
        return switch (p.scopeType) {
            case "tenant" -> AnalyticsKeys.tenantScopeUvKey(p.tenantId, day);
            case "application" -> AnalyticsKeys.applicationScopeUvKey(p.tenantId, p.scopeId, day);
            case "domain" -> AnalyticsKeys.domainScopeUvKey(p.tenantId, p.scopeId, day);
            default -> null;
        };
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

    private static List<String> extractMembers(List<MapRecord<String, Object, Object>> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(records.size());
        for (MapRecord<String, Object, Object> record : records) {
            if (record == null || record.getValue() == null) {
                continue;
            }
            Object raw = record.getValue().get("member");
            if (raw == null) {
                continue;
            }
            String member = String.valueOf(raw).trim();
            if (!member.isBlank()) {
                out.add(member);
            }
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

    private static MemberParts parseDirtyLinkMember(String member) {
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

    private static ScopeMemberParts parseScopeMember(String member) {
        if (member == null || member.isBlank()) {
            return null;
        }
        String[] parts = member.split(":", 3);
        if (parts.length != 3) {
            return null;
        }
        String scopeType = parts[0].trim().toLowerCase();
        if (!scopeType.equals("tenant") && !scopeType.equals("application") && !scopeType.equals("domain")) {
            return null;
        }
        try {
            long tenantId = Long.parseLong(parts[1]);
            long scopeId = Long.parseLong(parts[2]);
            if (tenantId <= 0) {
                return null;
            }
            if (scopeType.equals("tenant")) {
                scopeId = 0L;
            } else if (scopeId <= 0) {
                return null;
            }
            return new ScopeMemberParts(scopeType, tenantId, scopeId);
        } catch (Exception e) {
            return null;
        }
    }

    private record ScopeMemberParts(String scopeType, long tenantId, long scopeId) {

        private String key() {
            return scopeType + ":" + tenantId + ":" + scopeId;
        }
    }
}
