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

/**
 * 将重定向访问转换为 Redis Stream 事件的基础设施适配器。
 *
 * <p>此处始终写入 {@link AnalyticsKeys#visitEventStreamKey()}，即使 {@code app.analytics.events.enabled}
 * 为 {@code false} 或明细采样率为零也一样。访问流同时是核心 PV/UV 投影的输入；{@code events}
 * 配置只控制后续明细落库，不能阻断聚合统计。</p>
 *
 * <p>调用方决定 Redis 故障是否 fail-open。本适配器不吞掉写流异常，避免把“事件未入流”伪装成成功；
 * {@code AnalyticsVisitEventService} 会按照 {@code app.analytics.events.fail-open} 决定是否影响重定向。</p>
 */
@Component
public class RedisAnalyticsVisitEventAppender implements AnalyticsVisitEventAppender {

    private final StringRedisTemplate redis;
    private final AnalyticsProperties analyticsProperties;

    public RedisAnalyticsVisitEventAppender(StringRedisTemplate redis, AnalyticsProperties analyticsProperties) {
        this.redis = redis;
        this.analyticsProperties = analyticsProperties;
    }

    /**
     * 写入单次访问的规范化事件字段，并按配置近似裁剪 Stream 长度。
     *
     * <p>{@code requestId} 在这里生成，供明细表的幂等写入使用。时间按 UTC 日切分，以便 visitor
     * 指纹、Redis 聚合 key 与日汇总表共享同一个统计日。原始 IP 不会进入 Stream；只保存带盐指纹和
     * 归一化后的维度。</p>
     *
     * @param event 由重定向链路提供的访问上下文；为空时无副作用
     */
    @Override
    public void append(AnalyticsVisitEventService.RedirectVisitEvent event) {
        if (redis == null || event == null) {
            return;
        }

        AnalyticsProperties.Events cfg = analyticsProperties == null ? null : analyticsProperties.getEvents();
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

        long maxLen = analyticsProperties == null ? 0L : analyticsProperties.resolveVisitStreamMaxLen();
        if (maxLen > 0) {
            redis.opsForStream().trim(streamKey, maxLen, true);
        }
    }

    private static void putIfNonBlank(Map<String, String> fields, String key, String value) {
        if (fields == null || key == null || key.isBlank() || value == null || value.isBlank()) {
            return;
        }
        fields.put(key, value);
    }
}
