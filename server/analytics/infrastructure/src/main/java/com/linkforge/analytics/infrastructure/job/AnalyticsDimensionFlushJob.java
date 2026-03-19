package com.linkforge.analytics.infrastructure.job;

import com.linkforge.analytics.infrastructure.persistence.mapper.LinkStatsDimDailyMapper;
import com.linkforge.analytics.infrastructure.persistence.mapper.LinkStatsDimDailyUpsertRow;
import com.linkforge.contract.analytics.AnalyticsKeys;
import com.linkforge.foundation.config.AnalyticsProperties;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 维度按天聚合落库作业：将 Redis 维度 PV（Hash）聚合写入 MySQL。
 */
@Component
public class AnalyticsDimensionFlushJob {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsDimensionFlushJob.class);
    private static final String GROUP = "lf-dim-flush";
    private static final String CONSUMER = "lf-dim-flush-consumer";
    private static final int BATCH_SIZE = 500;

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

    public AnalyticsDimensionFlushJob(
            StringRedisTemplate redis,
            LinkStatsDimDailyMapper linkStatsDimDailyMapper,
            AnalyticsProperties analyticsProperties
    ) {
        this.redis = redis;
        this.linkStatsDimDailyMapper = linkStatsDimDailyMapper;
        this.analyticsProperties = analyticsProperties;
    }

    @Scheduled(fixedDelayString = "${APP_ANALYTICS_DIM_FLUSH_DELAY_MS:60000}")
    @SchedulerLock(name = "lf:job:analytics:dim-flush", lockAtMostFor = "PT15M")
    public void flush() {
        AnalyticsProperties.Dimensions cfg = analyticsProperties == null ? null : analyticsProperties.getDimensions();
        if (cfg == null || !cfg.isEnabled()) {
            return;
        }

        LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
        int backfillDays = resolveBackfillDays();
        for (int i = 0; i < backfillDays; i++) {
            flushDay(todayUtc.minusDays(i), cfg);
        }
    }

    private void flushDay(LocalDate day, AnalyticsProperties.Dimensions cfg) {
        String streamKey = AnalyticsKeys.dimDirtyStreamKey(day);
        if (!ensureGroup(streamKey)) {
            return;
        }

        Consumer consumer = Consumer.from(GROUP, CONSUMER);
        while (true) {
            List<MapRecord<String, Object, Object>> records = readSafe(
                    consumer,
                    StreamReadOptions.empty().count(BATCH_SIZE),
                    StreamOffset.create(streamKey, ReadOffset.from("0-0"))
            );
            if (records == null || records.isEmpty()) {
                records = readSafe(
                        consumer,
                        StreamReadOptions.empty().count(BATCH_SIZE),
                        StreamOffset.create(streamKey, ReadOffset.lastConsumed())
                );
            }
            if (records == null || records.isEmpty()) {
                return;
            }

            List<String> members = extractMembers(records);
            if (members.isEmpty()) {
                acknowledge(streamKey, records.stream().map(MapRecord::getId).toList());
                continue;
            }
            if (!flushActiveMembers(day, cfg, members)) {
                return;
            }
            acknowledge(streamKey, records.stream().map(MapRecord::getId).toList());
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

    private boolean hasKey(String key) {
        try {
            Boolean exists = redis.hasKey(key);
            return exists != null && exists;
        } catch (Exception e) {
            return false;
        }
    }

    boolean flushActiveMembers(LocalDate day, AnalyticsProperties.Dimensions cfg, List<String> members) {
        long startNs = System.nanoTime();
        List<MemberParts> parts = new ArrayList<>(members.size());
        for (String m : members) {
            MemberParts p = parseActiveMember(m);
            if (p != null) {
                parts.add(p);
            }
        }
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
                    log.debug("scan dim hash failed: key={}, err={}", key, ex.getMessage());
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
        try {
            linkStatsDimDailyMapper.batchUpsert(batch);
            return true;
        } catch (DataAccessException e) {
            log.warn("flush dim batch failed: size={}, err={}", batch.size(), e.getMessage());
            return false;
        }
    }

    private boolean ensureGroup(String streamKey) {
        if (!hasKey(streamKey)) {
            return false;
        }
        try {
            redis.opsForStream().createGroup(streamKey, ReadOffset.from("0-0"), GROUP);
            return true;
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("busygroup")) {
                return true;
            }
            log.debug("create dim dirty stream group failed: streamKey={}, err={}", streamKey, msg);
            return false;
        }
    }

    private List<MapRecord<String, Object, Object>> readSafe(
            Consumer consumer,
            StreamReadOptions options,
            StreamOffset<String> offset
    ) {
        try {
            return redis.opsForStream().read(consumer, options, offset);
        } catch (Exception e) {
            log.debug("read dim dirty stream failed: streamKey={}, err={}", offset == null ? null : offset.getKey(), e.getMessage());
            return null;
        }
    }

    private void acknowledge(String streamKey, List<RecordId> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        try {
            redis.opsForStream().acknowledge(streamKey, GROUP, ids.toArray(new RecordId[0]));
        } catch (Exception e) {
            log.debug("ack dim dirty stream failed: streamKey={}, size={}, err={}", streamKey, ids.size(), e.getMessage());
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

    private static MemberParts parseActiveMember(String member) {
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
