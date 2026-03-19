package com.linkforge.analytics.infrastructure;

import com.linkforge.analytics.application.VisitDimensionNormalizer;
import com.linkforge.analytics.application.VisitorFingerprint;
import com.linkforge.contract.analytics.AnalyticsKeys;
import com.linkforge.contract.analytics.VisitContext;
import com.linkforge.contract.analytics.VisitRecorderPort;
import com.linkforge.foundation.config.AnalyticsProperties;
import com.linkforge.foundation.web.RequestId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
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
public class VisitRecorderService implements VisitRecorderPort {

    private static final Logger log = LoggerFactory.getLogger(VisitRecorderService.class);

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

    public VisitRecorderService(StringRedisTemplate redis, AnalyticsProperties analyticsProperties) {
        this.redis = redis;
        this.analyticsProperties = analyticsProperties;
    }

    @Override
    public void recordVisit(long tenantId, long linkId, VisitContext visitContext) {
        try {
            LocalDate day = LocalDate.now(ZoneOffset.UTC);

            String pvKey = AnalyticsKeys.pvKey(tenantId, linkId, day);
            String uvKey = AnalyticsKeys.uvKey(tenantId, linkId, day);
            String activeKey = AnalyticsKeys.activeSetKey(day);
            String activeMember = AnalyticsKeys.activeMember(tenantId, linkId);
            String statsDirtyStreamKey = AnalyticsKeys.statsDirtyStreamKey(day);
            String dimDirtyStreamKey = AnalyticsKeys.dimDirtyStreamKey(day);

            // PV：计数
            Long pv = redis.opsForValue().increment(pvKey);
            // UV：近似去重（HLL）
            String visitor = VisitorFingerprint.fingerprint(day, visitContext, analyticsProperties == null ? null : analyticsProperties.getSalt());
            Long uvAdd = redis.opsForHyperLogLog().add(uvKey, visitor);
            // 活跃索引：供 flush job 增量读取
            Long activeAdd = redis.opsForSet().add(activeKey, activeMember);

            Date expireAt = resolveDayExpireAtUtc(day);
            enqueueDirtyMember(statsDirtyStreamKey, activeMember, expireAt);
            if (expireAt != null) {
                // TTL 设置采用“有限重试”策略：首次设置失败时，在下一次访问中仍会再尝试一次，
                // 避免因 pv>1 / uvAdd=0 / activeAdd=0 导致 key 永久漏 TTL。
                boolean retryOnce = pv != null && pv == 2L;

                if (pv != null && pv <= 2L) {
                    expireAtQuietly(pvKey, expireAt);
                }
                if ((uvAdd != null && uvAdd > 0) || retryOnce) {
                    expireAtQuietly(uvKey, expireAt);
                }
                if ((activeAdd != null && activeAdd > 0) || retryOnce) {
                    expireAtQuietly(activeKey, expireAt);
                }
            }

            AnalyticsProperties a = analyticsProperties;
            boolean dimsEnabled = a != null && a.getDimensions() != null && a.getDimensions().isEnabled();
            boolean eventsEnabled = a != null && a.getEvents() != null && a.getEvents().isEnabled();
            if (dimsEnabled) {
                enqueueDirtyMember(dimDirtyStreamKey, activeMember, expireAt);
            }
            if (!dimsEnabled && !eventsEnabled) {
                return;
            }

            int maxUaLen = a != null && a.getEvents() != null ? a.getEvents().getMaxUserAgentLength() : VisitDimensionNormalizer.DEFAULT_MAX_UA_RAW_LEN;
            int maxTrackingLen = a != null && a.getEvents() != null ? a.getEvents().getMaxTrackingValueLength() : VisitDimensionNormalizer.DEFAULT_MAX_TRACKING_VALUE_LEN;
            VisitDimensionNormalizer.Normalized n = VisitDimensionNormalizer.normalize(
                    visitContext,
                    VisitDimensionNormalizer.DEFAULT_MAX_DIM_VALUE_LEN,
                    maxUaLen,
                    maxTrackingLen
            );

            if (dimsEnabled) {
                recordDimensions(tenantId, linkId, day, n, visitor, expireAt);
            }
            if (eventsEnabled) {
                recordVisitEvent(tenantId, linkId, visitContext, n);
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

    private void recordDimensions(
            long tenantId,
            long linkId,
            LocalDate day,
            VisitDimensionNormalizer.Normalized n,
            String visitor,
            Date expireAt
    ) {
        AnalyticsProperties.Dimensions cfg = analyticsProperties == null ? null : analyticsProperties.getDimensions();
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

            String pvKey = AnalyticsKeys.dimPvHashKey(tenantId, linkId, day, dimType);
            Long pv = redis.opsForHash().increment(pvKey, value, 1L);

            String uvKey = AnalyticsKeys.dimUvHllKey(tenantId, linkId, day, dimType, value);
            Long uvAdd = visitor == null || visitor.isBlank()
                    ? null
                    : redis.opsForHyperLogLog().add(uvKey, visitor);

            if (expireAt != null && pv != null && pv <= 2L) {
                expireAtQuietly(pvKey, expireAt);
            }
            if (expireAt != null) {
                boolean retryOnce = pv != null && pv == 2L;
                if ((uvAdd != null && uvAdd > 0) || retryOnce) {
                    expireAtQuietly(uvKey, expireAt);
                }
            }
        }
    }

    private void expireAtQuietly(String key, Date expireAt) {
        if (expireAt == null || key == null || key.isBlank()) {
            return;
        }
        try {
            redis.expireAt(key, expireAt);
        } catch (Exception e) {
            log.debug("expireAt failed: key={}, requestId={}, err={}", key, RequestId.get(), e.getMessage());
        }
    }

    private Date resolveDayExpireAtUtc(LocalDate day) {
        if (day == null) {
            return null;
        }
        long ttlDays = analyticsProperties == null ? 0 : analyticsProperties.getRedisKeyTtlDays();
        if (ttlDays <= 0) {
            return null;
        }
        return Date.from(day.plusDays(ttlDays).atStartOfDay(ZoneOffset.UTC).toInstant());
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

    private void recordVisitEvent(long tenantId, long linkId, VisitContext visitContext, VisitDimensionNormalizer.Normalized n) {
        AnalyticsProperties.Events cfg = analyticsProperties == null ? null : analyticsProperties.getEvents();
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

        String requestId = UUID.randomUUID().toString().replace("-", "");
        fields.put("requestId", requestId);

        String salt = analyticsProperties == null ? null : analyticsProperties.getSalt();
        putIfNonBlank(fields, "ipHash", VisitorFingerprint.ipHash(visitContext, salt));

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

    private void enqueueDirtyMember(String streamKey, String member, Date expireAt) {
        if (streamKey == null || streamKey.isBlank() || member == null || member.isBlank()) {
            return;
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("member", member);
        fields.put("ts", String.valueOf(System.currentTimeMillis()));
        redis.opsForStream().add(StreamRecords.newRecord().in(streamKey).ofStrings(fields));
        if (expireAt != null) {
            expireAtQuietly(streamKey, expireAt);
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
