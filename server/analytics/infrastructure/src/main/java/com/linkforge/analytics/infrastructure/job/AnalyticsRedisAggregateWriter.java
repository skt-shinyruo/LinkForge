package com.linkforge.analytics.infrastructure.job;

import com.linkforge.contract.analytics.AnalyticsKeys;
import com.linkforge.foundation.config.AnalyticsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将一条访问流事件投影到 Redis 的 PV、UV 与维度聚合。
 *
 * <p>链接 PV 使用计数器，UV 使用 HyperLogLog，因此 UV 是近似值而不是可用于精确结算的去重数。
 * 每次写入都会追加 dirty stream 消息，成员 wire format 固定为 {@code tenantId:linkId}；落库作业
 * 读取该成员对应的当前累计值，而不是把消息本身当作增量。</p>
 *
 * <p>本类不提供原子跨 key 事务。投影完成但 Stream ACK 失败时可能重放，进而重复增加 PV；HLL 对重复
 * visitor 较稳定，但整个链路不是 exactly-once。日表使用单调 {@code GREATEST} upsert 缓解重复落库，
 * 不应据此推断访问计数严格无重复。</p>
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

    private final StringRedisTemplate redis;
    private final AnalyticsProperties analyticsProperties;

    public AnalyticsRedisAggregateWriter(StringRedisTemplate redis, AnalyticsProperties analyticsProperties) {
        this.redis = redis;
        this.analyticsProperties = analyticsProperties;
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

        AnalyticsProperties.Dimensions dimensions = analyticsProperties == null ? null : analyticsProperties.getDimensions();
        if (dimensions == null || !dimensions.isEnabled()) {
            return;
        }

        enqueueDirtyMember(dimDirtyStreamKey, dirtyLinkMember, expireAt);
        List<String> types = dimensions.getTypes();
        if (types == null || types.isEmpty()) {
            types = DEFAULT_DIM_TYPES;
        }

        for (String rawType : types) {
            String dimType = normalizeDimType(rawType);
            if (dimType == null) {
                continue;
            }
            String dimValue = resolveDimValue(dimType, values);
            if (dimValue == null) {
                continue;
            }

            String dimPvKey = AnalyticsKeys.dimPvHashKey(tenantId, linkId, day, dimType);
            String dimUvKey = AnalyticsKeys.dimUvHllKey(tenantId, linkId, day, dimType, dimValue);

            redis.opsForHash().increment(dimPvKey, dimValue, 1L);
            if (visitorKey != null) {
                redis.opsForHyperLogLog().add(dimUvKey, visitorKey);
            }

            expireAtQuietly(dimPvKey, expireAt);
            expireAtQuietly(dimUvKey, expireAt);
        }
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
}
