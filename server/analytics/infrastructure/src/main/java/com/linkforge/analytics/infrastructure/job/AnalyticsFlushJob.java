package com.linkforge.analytics.infrastructure.job;

import com.linkforge.analytics.infrastructure.persistence.mapper.AnalyticsScopeStatsDailyMapper;
import com.linkforge.analytics.infrastructure.persistence.mapper.AnalyticsScopeStatsDailyUpsertRow;
import com.linkforge.analytics.infrastructure.persistence.mapper.LinkStatsDailyMapper;
import com.linkforge.analytics.infrastructure.persistence.mapper.LinkStatsDailyUpsertRow;
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
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AnalyticsFlushJob {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsFlushJob.class);
    private static final String GROUP = "lf-stats-flush";
    private static final String CONSUMER = "lf-stats-flush-consumer";
    private static final int BATCH_SIZE = 500;

    private final StringRedisTemplate redis;
    private final LinkStatsDailyMapper linkStatsDailyMapper;
    private final AnalyticsScopeStatsDailyMapper scopeStatsDailyMapper;
    private final AnalyticsProperties analyticsProperties;

    public AnalyticsFlushJob(StringRedisTemplate redis, LinkStatsDailyMapper linkStatsDailyMapper, AnalyticsProperties analyticsProperties) {
        this(redis, linkStatsDailyMapper, null, analyticsProperties);
    }

    @Autowired
    public AnalyticsFlushJob(
            StringRedisTemplate redis,
            LinkStatsDailyMapper linkStatsDailyMapper,
            AnalyticsScopeStatsDailyMapper scopeStatsDailyMapper,
            AnalyticsProperties analyticsProperties
    ) {
        this.redis = redis;
        this.linkStatsDailyMapper = linkStatsDailyMapper;
        this.scopeStatsDailyMapper = scopeStatsDailyMapper;
        this.analyticsProperties = analyticsProperties;
    }

    @Scheduled(fixedDelayString = "${APP_ANALYTICS_FLUSH_DELAY_MS:60000}")
    @SchedulerLock(name = "lf:job:analytics:flush", lockAtMostFor = "PT10M")
    public void flush() {
        LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
        int backfillDays = resolveBackfillDays();
        for (int i = 0; i < backfillDays; i++) {
            flushDay(todayUtc.minusDays(i));
        }
    }

    private void flushDay(LocalDate day) {
        flushLinkStatsDay(day);
        flushScopeStatsDay(day);
    }

    private void flushLinkStatsDay(LocalDate day) {
        String streamKey = AnalyticsKeys.statsDirtyStreamKey(day);
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
            if (!flushActiveMembers(day, members)) {
                return;
            }
            acknowledge(streamKey, records.stream().map(MapRecord::getId).toList());
        }
    }

    private void flushScopeStatsDay(LocalDate day) {
        if (scopeStatsDailyMapper == null) {
            return;
        }
        String streamKey = AnalyticsKeys.scopeDirtyStreamKey(day);
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
            if (!flushActiveScopeMembers(day, members)) {
                return;
            }
            acknowledge(streamKey, records.stream().map(MapRecord::getId).toList());
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

    private boolean hasKey(String key) {
        try {
            Boolean exists = redis.hasKey(key);
            return exists != null && exists;
        } catch (Exception e) {
            return false;
        }
    }

    boolean flushActiveMembers(LocalDate day, List<String> members) {
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

    boolean flushActiveScopeMembers(LocalDate day, List<String> members) {
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
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
            log.info("flush scope stats batch ok: day={}, written={}, latencyMs={}", day, batch.size(), latencyMs);
            return true;
        } catch (DataAccessException e) {
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
            log.debug("create stats dirty stream group failed: streamKey={}, err={}", streamKey, msg);
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
            log.debug("read stats dirty stream failed: streamKey={}, err={}", offset == null ? null : offset.getKey(), e.getMessage());
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
            log.debug("ack stats dirty stream failed: streamKey={}, size={}, err={}", streamKey, ids.size(), e.getMessage());
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
