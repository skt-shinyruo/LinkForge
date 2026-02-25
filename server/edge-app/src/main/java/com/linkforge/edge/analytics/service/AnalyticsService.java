package com.linkforge.edge.analytics.service;

import com.linkforge.analytics.service.AnalyticsKeys;
import com.linkforge.platform.config.AppProperties;
import com.linkforge.platform.web.RequestId;
import com.linkforge.platform.web.VisitInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.data.redis.connection.stream.StreamRecords;

/**
 * Redirect 侧轻量统计写入：PV 计数 + UV 近似去重（HLL）+ 活跃索引集合（避免 flush 作业全量扫描）。
 *
 * <p>约束：统计写入不应影响主链路，异常必须降级为“只记录日志”。</p>
 */
@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

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
    private final AppProperties properties;

    public AnalyticsService(StringRedisTemplate redis, AppProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public void recordVisit(long tenantId, long linkId, VisitInfo visitInfo) {
        try {
            LocalDate day = LocalDate.now(ZoneOffset.UTC);

            String pvKey = AnalyticsKeys.pvKey(tenantId, linkId, day);
            String uvKey = AnalyticsKeys.uvKey(tenantId, linkId, day);
            String activeKey = AnalyticsKeys.activeSetKey(day);
            String activeMember = AnalyticsKeys.activeMember(tenantId, linkId);

            // PV：计数
            Long pv = redis.opsForValue().increment(pvKey);
            // UV：近似去重（HLL）
            String visitor = VisitorFingerprint.fingerprint(day, visitInfo, properties.getAnalytics().getSalt());
            Long uvAdd = redis.opsForHyperLogLog().add(uvKey, visitor);
            // 活跃索引：供 flush job 增量读取
            Long activeAdd = redis.opsForSet().add(activeKey, activeMember);

            Duration ttl = Duration.ofDays(properties.getAnalytics().getRedisKeyTtlDays());
            if (pv != null && pv == 1L) {
                redis.expire(pvKey, ttl);
            }
            if (uvAdd != null && uvAdd > 0) {
                redis.expire(uvKey, ttl);
            }
            if (activeAdd != null && activeAdd > 0) {
                redis.expire(activeKey, ttl);
            }

            AppProperties.Analytics a = properties.getAnalytics();
            boolean dimsEnabled = a != null && a.getDimensions() != null && a.getDimensions().isEnabled();
            boolean eventsEnabled = a != null && a.getEvents() != null && a.getEvents().isEnabled();
            if (!dimsEnabled && !eventsEnabled) {
                return;
            }

            int maxUaLen = a != null && a.getEvents() != null ? a.getEvents().getMaxUserAgentLength() : VisitDimensionNormalizer.DEFAULT_MAX_UA_RAW_LEN;
            int maxTrackingLen = a != null && a.getEvents() != null ? a.getEvents().getMaxTrackingValueLength() : VisitDimensionNormalizer.DEFAULT_MAX_TRACKING_VALUE_LEN;
            VisitDimensionNormalizer.Normalized n = VisitDimensionNormalizer.normalize(
                    visitInfo,
                    VisitDimensionNormalizer.DEFAULT_MAX_DIM_VALUE_LEN,
                    maxUaLen,
                    maxTrackingLen
            );

            if (dimsEnabled) {
                recordDimensions(tenantId, linkId, day, n, ttl);
            }
            if (eventsEnabled) {
                recordVisitEvent(tenantId, linkId, visitInfo, n);
            }
        } catch (Exception e) {
            // 统计写入失败不影响主链路；保留 requestId 便于排障
            log.debug(
                    "record visit failed: tenantId={}, linkId={}, requestId={}, err={}",
                    tenantId,
                    linkId,
                    RequestId.get(),
                    e.getMessage()
            );
        }
    }

    private void recordDimensions(long tenantId, long linkId, LocalDate day, VisitDimensionNormalizer.Normalized n, Duration ttl) {
        AppProperties.Analytics.Dimensions cfg = properties.getAnalytics() == null ? null : properties.getAnalytics().getDimensions();
        List<String> types = cfg == null ? null : cfg.getTypes();
        if (types == null || types.isEmpty()) {
            types = DEFAULT_DIM_TYPES;
        }

        for (String t : types) {
            String dimType = t == null ? null : t.trim().toLowerCase();
            if (dimType == null || dimType.isBlank()) {
                continue;
            }
            String value = resolveDimValue(dimType, n);
            if (value == null || value.isBlank()) {
                continue;
            }

            String key = AnalyticsKeys.dimPvHashKey(tenantId, linkId, day, dimType);
            Long v = redis.opsForHash().increment(key, value, 1L);
            if (v != null && v == 1L) {
                redis.expire(key, ttl);
            }
        }
    }

    private static String resolveDimValue(String dimType, VisitDimensionNormalizer.Normalized n) {
        if (n == null) {
            return null;
        }
        return switch (dimType) {
            case "referer_domain" -> n.refererDomain();
            case "language" -> n.language();
            case "ua_family" -> n.userAgentFamily();
            case "os_family" -> n.osFamily();
            case "device_type" -> n.deviceType();
            case "utm_source" -> n.utmSource();
            case "utm_medium" -> n.utmMedium();
            case "utm_campaign" -> n.utmCampaign();
            default -> null;
        };
    }

    private void recordVisitEvent(long tenantId, long linkId, VisitInfo visitInfo, VisitDimensionNormalizer.Normalized n) {
        AppProperties.Analytics.Events cfg = properties.getAnalytics() == null ? null : properties.getAnalytics().getEvents();
        if (cfg == null || !cfg.isEnabled()) {
            return;
        }

        double sampleRate = cfg.getSampleRate();
        if (sampleRate <= 0) {
            return;
        }
        if (sampleRate < 1 && ThreadLocalRandom.current().nextDouble() >= sampleRate) {
            return;
        }

        String streamKey = AnalyticsKeys.visitEventStreamKey();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("ts", String.valueOf(System.currentTimeMillis()));
        fields.put("tenantId", String.valueOf(tenantId));
        fields.put("linkId", String.valueOf(linkId));

        String requestId = RequestId.get();
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString().replace("-", "");
        }
        fields.put("requestId", requestId);

        String salt = properties.getAnalytics() == null ? null : properties.getAnalytics().getSalt();
        putIfNonBlank(fields, "ipHash", VisitorFingerprint.ipHash(visitInfo, salt));

        putIfNonBlank(fields, "uaRaw", n == null ? null : n.userAgentRaw());
        putIfNonBlank(fields, "uaFamily", n == null ? null : n.userAgentFamily());
        putIfNonBlank(fields, "osFamily", n == null ? null : n.osFamily());
        putIfNonBlank(fields, "deviceType", n == null ? null : n.deviceType());
        putIfNonBlank(fields, "refererDomain", n == null ? null : n.refererDomain());
        putIfNonBlank(fields, "language", n == null ? null : n.language());
        putIfNonBlank(fields, "utmSource", n == null ? null : n.utmSource());
        putIfNonBlank(fields, "utmMedium", n == null ? null : n.utmMedium());
        putIfNonBlank(fields, "utmCampaign", n == null ? null : n.utmCampaign());

        redis.opsForStream().add(StreamRecords.newRecord().in(streamKey).ofStrings(fields));
        long maxLen = cfg.getStreamMaxLen();
        if (maxLen > 0) {
            redis.opsForStream().trim(streamKey, maxLen, true);
        }
    }

    private static void putIfNonBlank(Map<String, String> m, String key, String value) {
        if (m == null || key == null || key.isBlank()) {
            return;
        }
        if (value == null || value.isBlank()) {
            return;
        }
        m.put(key, value);
    }
}
