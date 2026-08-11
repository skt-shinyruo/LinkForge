package com.linkforge.analytics.infrastructure.job;

import com.linkforge.contract.analytics.AnalyticsKeys;
import com.linkforge.foundation.config.AnalyticsProperties;
import com.linkforge.foundation.observability.OperationalMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将一条访问流事件投影到 Redis 的 PV、UV 与维度聚合。
 *
 * <p>链接 PV 使用计数器，UV 使用 HyperLogLog，因此 UV 是近似值而不是可用于精确结算的去重数。
 * 每个首次投影的事件都会追加 dirty stream 消息，成员 wire format 固定为 {@code tenantId:linkId}；落库作业
 * 读取该成员对应的当前累计值，而不是把消息本身当作增量。</p>
 *
 * <p>标准访问事件携带 {@code requestId}，并通过单个 Lua 脚本原子完成去重、聚合和 dirty signal 写入；
 * ACK 失败后的同一事件重放不会重复增加 PV。历史上没有 requestId 的消息仍走兼容路径，因此只对标准
 * appender 产生的新事件提供幂等保证。</p>
 */
@Component
public class AnalyticsRedisAggregateWriter {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsRedisAggregateWriter.class);

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

    private static final DefaultRedisScript<Long> IDEMPOTENT_PROJECTION_SCRIPT = new DefaultRedisScript<>("""
            local markerTtl = tonumber(ARGV[1]) or 0
            local aggregateTtl = tonumber(ARGV[2]) or 0
            local visitor = ARGV[3] or ''
            local scopeCount = tonumber(ARGV[4]) or 0
            local dimCount = tonumber(ARGV[5]) or 0

            if redis.call('SETNX', KEYS[1], '1') == 0 then
                return 0
            end
            if markerTtl > 0 then
                redis.call('EXPIRE', KEYS[1], markerTtl)
            end

            local argIndex = 6
            local scopeMembers = {}
            for i = 1, scopeCount do
                scopeMembers[i] = ARGV[argIndex]
                argIndex = argIndex + 1
            end
            local dimValues = {}
            for i = 1, dimCount do
                dimValues[i] = ARGV[argIndex]
                argIndex = argIndex + 1
            end
            local dirtyMember = ARGV[argIndex]
            local eventTs = ARGV[argIndex + 1]

            local keyIndex = 2
            redis.call('INCR', KEYS[keyIndex])
            keyIndex = keyIndex + 1

            if visitor ~= '' then
                redis.call('PFADD', KEYS[keyIndex], visitor)
                keyIndex = keyIndex + 1
                for i = 1, scopeCount do
                    redis.call('PFADD', KEYS[keyIndex], visitor)
                    keyIndex = keyIndex + 1
                end
            end

            for i = 1, dimCount do
                redis.call('HINCRBY', KEYS[keyIndex], dimValues[i], 1)
                keyIndex = keyIndex + 1
                if visitor ~= '' then
                    redis.call('PFADD', KEYS[keyIndex], visitor)
                end
                keyIndex = keyIndex + 1
            end

            local statsDirtyStream = KEYS[keyIndex]
            keyIndex = keyIndex + 1
            local scopeDirtyStream = KEYS[keyIndex]
            keyIndex = keyIndex + 1
            local dimDirtyStream = KEYS[keyIndex]

            redis.call('XADD', statsDirtyStream, '*', 'member', dirtyMember, 'ts', eventTs)
            for i = 1, scopeCount do
                redis.call('XADD', scopeDirtyStream, '*', 'member', scopeMembers[i], 'ts', eventTs)
            end
            if dimCount > 0 then
                redis.call('XADD', dimDirtyStream, '*', 'member', dirtyMember, 'ts', eventTs)
            end

            if aggregateTtl > 0 then
                for i = 2, #KEYS do
                    redis.call('EXPIRE', KEYS[i], aggregateTtl)
                end
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final AnalyticsProperties analyticsProperties;
    private final OperationalMetrics metrics;

    public AnalyticsRedisAggregateWriter(StringRedisTemplate redis, AnalyticsProperties analyticsProperties) {
        this(redis, analyticsProperties, OperationalMetrics.noop());
    }

    @Autowired
    public AnalyticsRedisAggregateWriter(
            StringRedisTemplate redis,
            AnalyticsProperties analyticsProperties,
            OperationalMetrics metrics
    ) {
        this.redis = redis;
        this.analyticsProperties = analyticsProperties;
        this.metrics = metrics == null ? OperationalMetrics.noop() : metrics;
    }

    /**
     * 按事件中的 UTC 时间写入链接、租户/应用/域范围及可选维度聚合。
     *
     * <p>缺少或非法的 tenant/link 标识会被静默丢弃，避免坏消息阻塞 consumer group。Redis 操作失败
     * 则向调用者抛出，由投影作业保留 pending 消息重试。</p>
     *
     * @param values Redis Stream 反序列化后的字段；至少需要 {@code tenantId} 和 {@code linkId}
     */
    public void write(Map<String, String> values) {
        if (redis == null || values == null || values.isEmpty()) {
            return;
        }

        long tenantId = safeLong(values.get("tenantId"), -1L);
        long linkId = safeLong(values.get("linkId"), -1L);
        if (tenantId <= 0 || linkId <= 0) {
            return;
        }
        long applicationId = safeLong(values.get("applicationId"), -1L);
        long domainId = safeLong(values.get("domainId"), -1L);

        long ts = safeLong(values.get("ts"), System.currentTimeMillis());
        LocalDate day = Instant.ofEpochMilli(ts).atOffset(ZoneOffset.UTC).toLocalDate();
        String visitorKey = trimToNull(values.get("visitorKey"));

        String pvKey = AnalyticsKeys.pvKey(tenantId, linkId, day);
        String uvKey = AnalyticsKeys.uvKey(tenantId, linkId, day);
        String dirtyLinkMember = AnalyticsKeys.dirtyLinkMember(tenantId, linkId);
        String statsDirtyStreamKey = AnalyticsKeys.statsDirtyStreamKey(day);
        String scopeDirtyStreamKey = AnalyticsKeys.scopeDirtyStreamKey(day);
        String dimDirtyStreamKey = AnalyticsKeys.dimDirtyStreamKey(day);
        Date expireAt = resolveDayExpireAtUtc(day);

        String requestId = trimToNull(values.get("requestId"));
        if (requestId != null) {
            writeIdempotently(
                    values,
                    requestId,
                    tenantId,
                    linkId,
                    applicationId,
                    domainId,
                    day,
                    visitorKey,
                    dirtyLinkMember,
                    statsDirtyStreamKey,
                    scopeDirtyStreamKey,
                    dimDirtyStreamKey,
                    expireAt
            );
            return;
        }

        redis.opsForValue().increment(pvKey);
        if (visitorKey != null) {
            redis.opsForHyperLogLog().add(uvKey, visitorKey);
            writeScopeUv(AnalyticsKeys.tenantScopeUvKey(tenantId, day), scopeDirtyStreamKey,
                    AnalyticsKeys.tenantScopeMember(tenantId), visitorKey, expireAt);
            if (applicationId > 0) {
                writeScopeUv(AnalyticsKeys.applicationScopeUvKey(tenantId, applicationId, day), scopeDirtyStreamKey,
                        AnalyticsKeys.applicationScopeMember(tenantId, applicationId), visitorKey, expireAt);
            }
            if (domainId > 0) {
                writeScopeUv(AnalyticsKeys.domainScopeUvKey(tenantId, domainId, day), scopeDirtyStreamKey,
                        AnalyticsKeys.domainScopeMember(tenantId, domainId), visitorKey, expireAt);
            }
        }
        // 不维护无界 active set；每次变更以 dirty stream 驱动后续持久化。
        enqueueDirtyMember(statsDirtyStreamKey, dirtyLinkMember, expireAt);

        expireAtQuietly(pvKey, expireAt);
        expireAtQuietly(uvKey, expireAt);

        List<DimensionProjection> dimensions = resolveDimensions(values);
        if (dimensions.isEmpty()) {
            return;
        }

        enqueueDirtyMember(dimDirtyStreamKey, dirtyLinkMember, expireAt);
        for (DimensionProjection dimension : dimensions) {
            String dimPvKey = AnalyticsKeys.dimPvHashKey(tenantId, linkId, day, dimension.type());
            String dimUvKey = AnalyticsKeys.dimUvHllKey(tenantId, linkId, day, dimension.type(), dimension.value());

            redis.opsForHash().increment(dimPvKey, dimension.value(), 1L);
            if (visitorKey != null) {
                redis.opsForHyperLogLog().add(dimUvKey, visitorKey);
            }

            expireAtQuietly(dimPvKey, expireAt);
            expireAtQuietly(dimUvKey, expireAt);
        }
    }

    private void writeIdempotently(
            Map<String, String> values,
            String requestId,
            long tenantId,
            long linkId,
            long applicationId,
            long domainId,
            LocalDate day,
            String visitorKey,
            String dirtyLinkMember,
            String statsDirtyStreamKey,
            String scopeDirtyStreamKey,
            String dimDirtyStreamKey,
            Date expireAt
    ) {
        List<ScopeProjection> scopes = new ArrayList<>(3);
        if (visitorKey != null) {
            scopes.add(new ScopeProjection(
                    AnalyticsKeys.tenantScopeUvKey(tenantId, day),
                    AnalyticsKeys.tenantScopeMember(tenantId)
            ));
            if (applicationId > 0) {
                scopes.add(new ScopeProjection(
                        AnalyticsKeys.applicationScopeUvKey(tenantId, applicationId, day),
                        AnalyticsKeys.applicationScopeMember(tenantId, applicationId)
                ));
            }
            if (domainId > 0) {
                scopes.add(new ScopeProjection(
                        AnalyticsKeys.domainScopeUvKey(tenantId, domainId, day),
                        AnalyticsKeys.domainScopeMember(tenantId, domainId)
                ));
            }
        }
        List<DimensionProjection> dimensions = resolveDimensions(values);

        List<String> keys = new ArrayList<>(8 + scopes.size() + dimensions.size() * 2);
        keys.add(AnalyticsKeys.projectionDedupKey(requestId));
        keys.add(AnalyticsKeys.pvKey(tenantId, linkId, day));
        if (visitorKey != null) {
            keys.add(AnalyticsKeys.uvKey(tenantId, linkId, day));
            scopes.stream().map(ScopeProjection::uvKey).forEach(keys::add);
        }
        for (DimensionProjection dimension : dimensions) {
            keys.add(AnalyticsKeys.dimPvHashKey(tenantId, linkId, day, dimension.type()));
            keys.add(AnalyticsKeys.dimUvHllKey(tenantId, linkId, day, dimension.type(), dimension.value()));
        }
        keys.add(statsDirtyStreamKey);
        keys.add(scopeDirtyStreamKey);
        keys.add(dimDirtyStreamKey);

        long nowMillis = System.currentTimeMillis();
        List<String> args = new ArrayList<>(7 + scopes.size() + dimensions.size());
        args.add(String.valueOf(resolveProjectionMarkerTtlSeconds()));
        args.add(String.valueOf(resolveAggregateTtlSeconds(expireAt, nowMillis)));
        args.add(visitorKey == null ? "" : visitorKey);
        args.add(String.valueOf(scopes.size()));
        args.add(String.valueOf(dimensions.size()));
        scopes.stream().map(ScopeProjection::dirtyMember).forEach(args::add);
        dimensions.stream().map(DimensionProjection::value).forEach(args::add);
        args.add(dirtyLinkMember);
        args.add(String.valueOf(nowMillis));

        long startedAt = System.nanoTime();
        try {
            Long result = redis.execute(IDEMPOTENT_PROJECTION_SCRIPT, keys, args.toArray());
            if (result == null) {
                throw new IllegalStateException("analytics projection script returned null");
            }
            String resultTag = result == 0L ? "deduplicated" : "applied";
            metrics.increment("linkforge.analytics.projection.events", "result", resultTag);
            if (result != 0L) {
                long dirtySignals = 1L + scopes.size() + (dimensions.isEmpty() ? 0L : 1L);
                metrics.add("linkforge.analytics.dirty.signals", dirtySignals, "source", "projection");
            }
            metrics.record(
                    "linkforge.analytics.projection.duration",
                    Duration.ofNanos(System.nanoTime() - startedAt),
                    "result",
                    resultTag
            );
        } catch (RuntimeException ex) {
            metrics.increment("linkforge.analytics.projection.events", "result", "failure");
            metrics.record(
                    "linkforge.analytics.projection.duration",
                    Duration.ofNanos(System.nanoTime() - startedAt),
                    "result",
                    "failure"
            );
            throw ex;
        }
    }

    private List<DimensionProjection> resolveDimensions(Map<String, String> values) {
        AnalyticsProperties.Dimensions cfg = analyticsProperties == null ? null : analyticsProperties.getDimensions();
        if (cfg == null || !cfg.isEnabled()) {
            return List.of();
        }
        List<String> types = cfg.getTypes();
        if (types == null || types.isEmpty()) {
            types = DEFAULT_DIM_TYPES;
        }
        List<DimensionProjection> dimensions = new ArrayList<>(types.size());
        for (String rawType : types) {
            String dimType = normalizeDimType(rawType);
            if (dimType == null) {
                continue;
            }
            String dimValue = resolveDimValue(dimType, values);
            if (dimValue != null) {
                dimensions.add(new DimensionProjection(dimType, dimValue));
            }
        }
        return List.copyOf(dimensions);
    }

    private long resolveProjectionMarkerTtlSeconds() {
        long ttlDays = analyticsProperties == null ? 0L : analyticsProperties.getRedisKeyTtlDays();
        AnalyticsProperties.Events events = analyticsProperties == null ? null : analyticsProperties.getEvents();
        long retentionDays = events == null ? 0L : events.getRetentionDays();
        return Math.max(Math.max(ttlDays, retentionDays), 1L) * 86_400L;
    }

    private static long resolveAggregateTtlSeconds(Date expireAt, long nowMillis) {
        if (expireAt == null) {
            return 0L;
        }
        long remainingMillis = expireAt.getTime() - nowMillis;
        return Math.max((remainingMillis + 999L) / 1000L, 1L);
    }

    private void writeScopeUv(String uvKey, String dirtyStreamKey, String dirtyMember, String visitorKey, Date expireAt) {
        if (uvKey == null || uvKey.isBlank() || dirtyMember == null || dirtyMember.isBlank() || visitorKey == null) {
            return;
        }
        redis.opsForHyperLogLog().add(uvKey, visitorKey);
        expireAtQuietly(uvKey, expireAt);
        enqueueDirtyMember(dirtyStreamKey, dirtyMember, expireAt);
    }

    private void enqueueDirtyMember(String streamKey, String member, Date expireAt) {
        if (streamKey == null || streamKey.isBlank() || member == null || member.isBlank()) {
            return;
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("member", member);
        fields.put("ts", String.valueOf(System.currentTimeMillis()));
        redis.opsForStream().add(StreamRecords.newRecord().in(streamKey).ofStrings(fields));
        expireAtQuietly(streamKey, expireAt);
    }

    private void expireAtQuietly(String key, Date expireAt) {
        if (expireAt == null || key == null || key.isBlank()) {
            return;
        }
        try {
            redis.expireAt(key, expireAt);
        } catch (Exception e) {
            log.debug("expire analytics aggregate key failed: key={}, err={}", key, e.getMessage());
        }
    }

    private Date resolveDayExpireAtUtc(LocalDate day) {
        if (day == null) {
            return null;
        }
        long ttlDays = analyticsProperties == null ? 0L : analyticsProperties.getRedisKeyTtlDays();
        if (ttlDays <= 0) {
            return null;
        }
        return Date.from(day.plusDays(ttlDays).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    private static String resolveDimValue(String dimType, Map<String, String> values) {
        if (dimType == null || values == null || values.isEmpty()) {
            return null;
        }
        return switch (dimType) {
            case "referer_domain" -> trimToNull(values.get("refererDomain"));
            case "language" -> trimToNull(values.get("language"));
            case "ua_family" -> trimToNull(values.get("uaFamily"));
            case "os_family" -> trimToNull(values.get("osFamily"));
            case "device_type" -> trimToNull(values.get("deviceType"));
            case "utm_source" -> trimToNull(values.get("utmSource"));
            case "utm_medium" -> trimToNull(values.get("utmMedium"));
            case "utm_campaign" -> trimToNull(values.get("utmCampaign"));
            default -> null;
        };
    }

    private static String normalizeDimType(String rawType) {
        if (rawType == null) {
            return null;
        }
        String dimType = rawType.trim().toLowerCase();
        return dimType.isBlank() ? null : dimType;
    }

    private static long safeLong(String raw, long defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private record ScopeProjection(String uvKey, String dirtyMember) {
    }

    private record DimensionProjection(String type, String value) {
    }
}
