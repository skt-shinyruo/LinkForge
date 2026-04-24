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

    public void write(Map<String, String> values) {
        if (redis == null || values == null || values.isEmpty()) {
            return;
        }

        long tenantId = safeLong(values.get("tenantId"), -1L);
        long linkId = safeLong(values.get("linkId"), -1L);
        if (tenantId <= 0 || linkId <= 0) {
            return;
        }

        long ts = safeLong(values.get("ts"), System.currentTimeMillis());
        LocalDate day = Instant.ofEpochMilli(ts).atOffset(ZoneOffset.UTC).toLocalDate();
        String visitorKey = trimToNull(values.get("visitorKey"));

        String pvKey = AnalyticsKeys.pvKey(tenantId, linkId, day);
        String uvKey = AnalyticsKeys.uvKey(tenantId, linkId, day);
        String activeKey = AnalyticsKeys.activeSetKey(day);
        String activeMember = AnalyticsKeys.activeMember(tenantId, linkId);
        String statsDirtyStreamKey = AnalyticsKeys.statsDirtyStreamKey(day);
        String dimDirtyStreamKey = AnalyticsKeys.dimDirtyStreamKey(day);
        Date expireAt = resolveDayExpireAtUtc(day);

        redis.opsForValue().increment(pvKey);
        if (visitorKey != null) {
            redis.opsForHyperLogLog().add(uvKey, visitorKey);
        }
        redis.opsForSet().add(activeKey, activeMember);
        enqueueDirtyMember(statsDirtyStreamKey, activeMember, expireAt);

        expireAtQuietly(pvKey, expireAt);
        expireAtQuietly(uvKey, expireAt);
        expireAtQuietly(activeKey, expireAt);

        AnalyticsProperties.Dimensions dimensions = analyticsProperties == null ? null : analyticsProperties.getDimensions();
        if (dimensions == null || !dimensions.isEnabled()) {
            return;
        }

        enqueueDirtyMember(dimDirtyStreamKey, activeMember, expireAt);
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
