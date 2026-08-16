package com.linkforge.analytics.infrastructure.job;

import com.linkforge.analytics.infrastructure.persistence.mapper.LinkStatsDimDailyMapper;
import com.linkforge.analytics.infrastructure.persistence.mapper.LinkStatsDimDailyUpsertRow;
import com.linkforge.contract.analytics.AnalyticsKeys;
import com.linkforge.foundation.config.AnalyticsProperties;
import com.linkforge.foundation.observability.OperationalMetrics;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 Redis 维度 PV Hash 和 UV HyperLogLog 快照写入 MySQL 日表。
 *
 * <p>每个 V2 marker field 固定为 {@code tenantId:linkId}，每个维度 PV Hash 的 field 是维度值，UV 是
 * 维度值对应的独立 HLL key。HLL 的 {@code PFCOUNT} 是近似 UV；维度值在 key 中以 SHA-256 后缀编码，
 * 以避免不受控文本膨胀 Redis key。</p>
 *
 * <p>扫描 Redis 或写数据库失败会保留 V2 marker；legacy 兼容消息则保留 pending。落库使用单调
 * {@code GREATEST} upsert，所以重放不会降低快照，但 Redis 与数据库并不组成 exactly-once 事务。</p>
 *
 * <p>legacy Stream 的 retained entries 与实际 {@code lag + pending} 分开观测，并跨全部回填日期汇总；
 * drained 只累计 Redis 实际 ACK 的记录。</p>
 */
@Component
public class AnalyticsDimensionFlushJob {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsDimensionFlushJob.class);
    private static final String GROUP = "lf-dim-flush";
    private static final String CONSUMER = "lf-dim-flush-consumer";
    private static final int BATCH_SIZE = 500;
    private static final int MAX_MARKER_BATCHES_PER_DAY = 10;

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
    private final LinkStatsDimDailyMapper linkStatsDimDailyMapper;
    private final AnalyticsProperties analyticsProperties;
    private final RedisStreamBatchConsumer streamConsumer;
    private final LegacyDirtyStreamMetrics legacyStreamMetrics;
    private final VersionedDirtyMarkerStore markerStore;
    private final OperationalMetrics metrics;

    public AnalyticsDimensionFlushJob(
            StringRedisTemplate redis,
            LinkStatsDimDailyMapper linkStatsDimDailyMapper,
            AnalyticsProperties analyticsProperties
    ) {
        this(redis, linkStatsDimDailyMapper, analyticsProperties, OperationalMetrics.noop());
    }

    @Autowired
    public AnalyticsDimensionFlushJob(
            StringRedisTemplate redis,
            LinkStatsDimDailyMapper linkStatsDimDailyMapper,
            AnalyticsProperties analyticsProperties,
            OperationalMetrics metrics
    ) {
        this.redis = redis;
        this.linkStatsDimDailyMapper = linkStatsDimDailyMapper;
        this.analyticsProperties = analyticsProperties;
        this.metrics = metrics == null ? OperationalMetrics.noop() : metrics;
        this.streamConsumer = new RedisStreamBatchConsumer(
                redis, GROUP, CONSUMER, "analytics_dimension_flush", this.metrics, false);
        this.legacyStreamMetrics = new LegacyDirtyStreamMetrics(redis, streamConsumer, this.metrics);
        this.markerStore = new VersionedDirtyMarkerStore(redis);
    }

    /**
     * 扫描 UTC 当日及可回填日期的维度 V2 marker，并按配置兼容读取 legacy Stream。
     *
     * <p>禁用 {@code analytics.dimensions.enabled} 时整条维度链路停止；链接级 PV/UV 与明细链路不受
     * 此开关影响。</p>
     */
    @Scheduled(fixedDelayString = "${APP_ANALYTICS_DIM_FLUSH_DELAY_MS:60000}")
    @SchedulerLock(name = "lf:job:analytics:dim-flush", lockAtMostFor = "PT15M")
    public void flush() {
        AnalyticsProperties.Dimensions cfg = analyticsProperties == null ? null : analyticsProperties.getDimensions();
        if (cfg == null || !cfg.isEnabled()) {
            return;
        }

        LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
        int backfillDays = resolveBackfillDays();
        LegacyDirtyStreamMetrics.Aggregate legacy = legacyReadEnabled()
                ? legacyStreamMetrics.start("dimension") : null;
        for (int i = 0; i < backfillDays; i++) {
            flushDay(todayUtc.minusDays(i), cfg, legacy);
        }
        if (legacy != null) {
            streamConsumer.publishStreamState(legacy.publish());
        }
    }

    private void flushDay(
            LocalDate day,
            AnalyticsProperties.Dimensions cfg,
            LegacyDirtyStreamMetrics.Aggregate legacy
    ) {
        flushVersionedMarkers(day, cfg);
        if (!legacyReadEnabled()) {
            return;
        }
        String streamKey = AnalyticsKeys.dimDirtyStreamKey(day);
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
                if (!members.isEmpty() && !flushDirtyMembers(day, cfg, members)) {
                    return;
                }
                if (!acknowledgeLegacy(streamKey, records)) {
                    return;
                }
            }
        } finally {
            legacy.observe(streamKey);
        }
    }

    private void flushVersionedMarkers(LocalDate day, AnalyticsProperties.Dimensions cfg) {
        String markerKey = AnalyticsKeys.dimDirtyMarkerV2Key(day);
        String firstSeenKey = AnalyticsKeys.dimDirtyMarkerV2FirstSeenKey(day);
        for (int batch = 0; batch < MAX_MARKER_BATCHES_PER_DAY; batch++) {
            List<VersionedDirtyMarkerStore.Claim> claims;
            try {
                Long cardinality = redis.opsForHash().size(markerKey);
                metrics.set("linkforge.analytics.dirty.marker.cardinality",
                        cardinality == null ? 0L : Math.max(cardinality, 0L), "marker", "dimension");
                claims = markerStore.claim(markerKey, firstSeenKey, BATCH_SIZE);
                long oldest = claims.stream()
                        .mapToLong(VersionedDirtyMarkerStore.Claim::firstSeenEpochMillis)
                        .filter(value -> value > 0)
                        .min()
                        .orElse(0L);
                metrics.set("linkforge.analytics.dirty.marker.oldest_age_millis",
                        oldest == 0L ? 0L : Math.max(System.currentTimeMillis() - oldest, 0L),
                        "marker", "dimension");
            } catch (RuntimeException ex) {
                metrics.increment("linkforge.job.failures", "job", "analytics_dimension_flush", "stage", "marker_claim");
                log.debug("claim versioned dimension markers failed: err={}", ex.getMessage());
                return;
            }
            if (claims.isEmpty()) {
                return;
            }
            if (!flushDirtyMembers(day, cfg, claims.stream().map(VersionedDirtyMarkerStore.Claim::member).toList())) {
                return;
            }
            try {
                VersionedDirtyMarkerStore.Completion completion = markerStore.complete(markerKey, firstSeenKey, claims);
                metrics.add("linkforge.analytics.dirty.marker.completed", completion.completed(), "marker", "dimension");
                metrics.add("linkforge.analytics.dirty.marker.generation_conflicts",
                        completion.generationConflicts(), "marker", "dimension");
            } catch (RuntimeException ex) {
                metrics.increment("linkforge.job.failures", "job", "analytics_dimension_flush", "stage", "marker_complete");
                log.debug("complete versioned dimension markers failed: err={}", ex.getMessage());
                return;
            }
        }
    }

    private boolean legacyReadEnabled() {
        AnalyticsProperties.DirtyMarker cfg = analyticsProperties == null ? null : analyticsProperties.getDirtyMarker();
        return cfg == null || cfg.isLegacyReadEnabled();
    }

    private boolean acknowledgeLegacy(String streamKey, List<MapRecord<String, Object, Object>> records) {
        List<RecordId> ids = records.stream().map(MapRecord::getId).toList();
        long acknowledged = streamConsumer.acknowledge(streamKey, ids);
        reportLegacyDrained(acknowledged);
        return acknowledged == ids.size();
    }

    private void reportLegacyDrained(long records) {
        if (records > 0) {
            metrics.add("linkforge.analytics.dirty.legacy.drained", records, "marker", "dimension");
        }
    }

    private int resolveBackfillDays() {
        AnalyticsProperties a = analyticsProperties;
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
     * 扫描 V2 marker 或 legacy Stream 指定链接的维度 Hash，并把当前累计值写入 MySQL。
     *
     * @return {@code true} 表示本批消息可以 ACK；Redis 扫描或数据库写入失败时返回 {@code false}，
     *         由 consumer group pending 消息承担重试
     */
    boolean flushDirtyMembers(LocalDate day, AnalyticsProperties.Dimensions cfg, List<String> members) {
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

        List<String> types = cfg.getTypes();
        if (types == null || types.isEmpty()) {
            types = DEFAULT_DIM_TYPES;
        }

        List<LinkStatsDimDailyUpsertRow> batch = new ArrayList<>(800);
        long flushedRows = 0;

        for (MemberParts p : parts) {
            for (String rawType : types) {
                String dimType = rawType == null ? null : rawType.trim().toLowerCase();
                if (dimType == null || dimType.isBlank()) {
                    continue;
                }
                String key = AnalyticsKeys.dimPvHashKey(p.tenantId, p.linkId, day, dimType);

                @SuppressWarnings("unchecked")
                HashOperations<String, Object, Object> hashOps = redis.opsForHash();
                try (Cursor<Map.Entry<Object, Object>> cursor = hashOps.scan(key, org.springframework.data.redis.core.ScanOptions.scanOptions().count(1000).build())) {
                    List<String> dimValues = new ArrayList<>(200);
                    List<Long> dimPvs = new ArrayList<>(200);

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
                        dimValues.add(dimValue);
                        dimPvs.add(pv);

                        if (dimValues.size() >= 200) {
                            appendRowsWithUv(batch, p, day, dimType, dimValues, dimPvs);
                            if (batch.size() >= 500) {
                                flushedRows += batch.size();
                                if (!flushBatch(batch)) {
                                    return false;
                                }
                                batch.clear();
                            }
                            dimValues.clear();
                            dimPvs.clear();
                        }
                    }

                    if (!dimValues.isEmpty()) {
                        appendRowsWithUv(batch, p, day, dimType, dimValues, dimPvs);
                        if (batch.size() >= 500) {
                            flushedRows += batch.size();
                            if (!flushBatch(batch)) {
                                return false;
                            }
                            batch.clear();
                        }
                    }
                } catch (Exception ex) {
                    metrics.increment("linkforge.job.failures", "job", "analytics_dimension_flush", "stage", "redis_scan");
                    log.debug("scan dim hash failed: key={}, err={}", key, ex.getMessage());
                    return false;
                }
            }
        }

        if (!batch.isEmpty()) {
            flushedRows += batch.size();
            if (!flushBatch(batch)) {
                return false;
            }
        }

        if (flushedRows > 0) {
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
            log.info("flush dim batch ok: day={}, links={}, rows={}, latencyMs={}", day, parts.size(), flushedRows, latencyMs);
        }
        return true;
    }

    private void appendRowsWithUv(
            List<LinkStatsDimDailyUpsertRow> batch,
            MemberParts p,
            LocalDate day,
            String dimType,
            List<String> dimValues,
            List<Long> dimPvs
    ) {
        if (dimValues == null || dimValues.isEmpty()) {
            return;
        }
        List<String> uvKeys = dimValues.stream()
                .map(v -> AnalyticsKeys.dimUvHllKey(p.tenantId, p.linkId, day, dimType, v))
                .toList();
        List<Long> uvs = pfCountPipeline(uvKeys);

        for (int i = 0; i < dimValues.size(); i++) {
            long pv = dimPvs == null || i >= dimPvs.size() || dimPvs.get(i) == null ? 0L : dimPvs.get(i);
            if (pv <= 0) {
                continue;
            }

            Long uvRaw = uvs == null || i >= uvs.size() ? null : uvs.get(i);
            long uv = safeLong(uvRaw, 0L);

            LinkStatsDimDailyUpsertRow row = new LinkStatsDimDailyUpsertRow();
            row.setTenantId(p.tenantId);
            row.setLinkId(p.linkId);
            row.setDay(day);
            row.setDimType(dimType);
            row.setDimValue(dimValues.get(i));
            row.setPv(pv);
            row.setUv(Math.max(uv, 0L));
            batch.add(row);
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

    private boolean flushBatch(List<LinkStatsDimDailyUpsertRow> batch) {
        if (batch == null || batch.isEmpty()) {
            return true;
        }
        long startedAt = System.nanoTime();
        try {
            linkStatsDimDailyMapper.batchUpsert(batch);
            metrics.add("linkforge.job.rows", batch.size(), "job", "analytics_dimension_flush", "result", "success");
            metrics.record("linkforge.job.db_batch", Duration.ofNanos(System.nanoTime() - startedAt), "job", "analytics_dimension_flush", "result", "success");
            return true;
        } catch (DataAccessException e) {
            metrics.increment("linkforge.job.failures", "job", "analytics_dimension_flush", "stage", "database");
            metrics.record("linkforge.job.db_batch", Duration.ofNanos(System.nanoTime() - startedAt), "job", "analytics_dimension_flush", "result", "failure");
            log.warn("flush dim batch failed: size={}, err={}", batch.size(), e.getMessage());
            return false;
        }
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
}
