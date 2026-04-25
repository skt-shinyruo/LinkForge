package com.linkforge.analytics.infrastructure;

import com.linkforge.analytics.application.AnalyticsVisitEventService;
import com.linkforge.analytics.application.VisitDimensionNormalizer;
import com.linkforge.analytics.application.VisitorFingerprint;
import com.linkforge.analytics.application.port.AnalyticsVisitEventAppender;
import com.linkforge.contract.analytics.AnalyticsKeys;
import com.linkforge.contract.analytics.VisitContext;
import com.linkforge.foundation.config.AnalyticsProperties;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class RedisAnalyticsVisitEventAppender implements AnalyticsVisitEventAppender {

    private final StringRedisTemplate redis;
    private final AnalyticsProperties analyticsProperties;

    public RedisAnalyticsVisitEventAppender(StringRedisTemplate redis, AnalyticsProperties analyticsProperties) {
        this.redis = redis;
        this.analyticsProperties = analyticsProperties;
    }

    @Override
    public void append(AnalyticsVisitEventService.RedirectVisitEvent event) {
        if (redis == null || event == null) {
            return;
        }

        AnalyticsProperties.Events cfg = analyticsProperties == null ? null : analyticsProperties.getEvents();
        if (!shouldAppend(cfg)) {
            return;
        }

        long occurredAtMillis = event.occurredAtMillis() > 0 ? event.occurredAtMillis() : System.currentTimeMillis();
        LocalDate day = Instant.ofEpochMilli(occurredAtMillis).atOffset(ZoneOffset.UTC).toLocalDate();
        VisitContext visitContext = new VisitContext(
                event.ip(),
                event.userAgent(),
                event.referer(),
                event.acceptLanguage(),
                event.trackingParams()
        );

        int maxUaLen = cfg == null
                ? VisitDimensionNormalizer.DEFAULT_MAX_UA_RAW_LEN
                : cfg.getMaxUserAgentLength();
        int maxTrackingLen = cfg == null
                ? VisitDimensionNormalizer.DEFAULT_MAX_TRACKING_VALUE_LEN
                : cfg.getMaxTrackingValueLength();

        VisitDimensionNormalizer.Normalized normalized = VisitDimensionNormalizer.normalize(
                visitContext,
                VisitDimensionNormalizer.DEFAULT_MAX_DIM_VALUE_LEN,
                maxUaLen,
                maxTrackingLen
        );

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("ts", String.valueOf(occurredAtMillis));
        fields.put("tenantId", String.valueOf(event.tenantId()));
        fields.put("linkId", String.valueOf(event.linkId()));
        fields.put("requestId", UUID.randomUUID().toString().replace("-", ""));
        putIfNonBlank(fields, "visitorKey", VisitorFingerprint.fingerprint(day, visitContext, analyticsProperties == null ? null : analyticsProperties.getSalt()));
        putIfNonBlank(fields, "ipHash", VisitorFingerprint.ipHash(visitContext, analyticsProperties == null ? null : analyticsProperties.getSalt()));
        putIfNonBlank(fields, "uaRaw", normalized.userAgentRaw());
        putIfNonBlank(fields, "uaFamily", normalized.userAgentFamily());
        putIfNonBlank(fields, "osFamily", normalized.osFamily());
        putIfNonBlank(fields, "deviceType", normalized.deviceType());
        putIfNonBlank(fields, "refererDomain", normalized.refererDomain());
        putIfNonBlank(fields, "language", normalized.language());
        putIfNonBlank(fields, "utmSource", normalized.utmSource());
        putIfNonBlank(fields, "utmMedium", normalized.utmMedium());
        putIfNonBlank(fields, "utmCampaign", normalized.utmCampaign());
        putIfNonBlank(fields, "applicationId", event.applicationId() == null ? null : String.valueOf(event.applicationId()));
        putIfNonBlank(fields, "domainId", event.domainId() == null ? null : String.valueOf(event.domainId()));
        putIfNonBlank(fields, "code", event.code());

        String streamKey = AnalyticsKeys.visitEventStreamKey();
        redis.opsForStream().add(StreamRecords.newRecord().in(streamKey).ofStrings(fields));

        long maxLen = cfg == null ? 0L : cfg.getStreamMaxLen();
        if (maxLen > 0) {
            redis.opsForStream().trim(streamKey, maxLen, true);
        }
    }

    private static boolean shouldAppend(AnalyticsProperties.Events cfg) {
        if (cfg == null || !cfg.isEnabled()) {
            return false;
        }
        double sampleRate = cfg.getSampleRate();
        if (Double.isNaN(sampleRate) || sampleRate <= 0) {
            return false;
        }
        if (sampleRate >= 1) {
            return true;
        }
        return ThreadLocalRandom.current().nextDouble() < sampleRate;
    }

    private static void putIfNonBlank(Map<String, String> fields, String key, String value) {
        if (fields == null || key == null || key.isBlank() || value == null || value.isBlank()) {
            return;
        }
        fields.put(key, value);
    }
}
