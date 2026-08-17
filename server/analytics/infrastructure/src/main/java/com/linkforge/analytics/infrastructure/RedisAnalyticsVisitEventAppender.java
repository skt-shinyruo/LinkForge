package com.linkforge.analytics.infrastructure;

import com.linkforge.analytics.application.AnalyticsVisitEventService;
import com.linkforge.analytics.application.VisitorFingerprint;
import com.linkforge.analytics.application.port.AnalyticsVisitEventAppender;
import com.linkforge.contract.analytics.VisitContext;
import com.linkforge.foundation.config.AnalyticsProperties;
import com.linkforge.analytics.infrastructure.job.AnalyticsRedisAggregateWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * 将重定向访问直接写入 Redis 聚合的基础设施适配器。
 *
 * <p>一次重定向直接进入原子聚合脚本，不经过中间 Stream。</p>
 *
 * <p>调用方决定 Redis 故障是否 fail-open。本适配器不吞掉聚合异常，避免把丢失统计伪装成成功；
 * {@code AnalyticsVisitEventService} 会按照 {@code app.analytics.events.fail-open} 决定是否影响重定向。</p>
 */
@Component
public class RedisAnalyticsVisitEventAppender implements AnalyticsVisitEventAppender {

    private final AnalyticsRedisAggregateWriter aggregateWriter;
    private final AnalyticsProperties analyticsProperties;

    public RedisAnalyticsVisitEventAppender(StringRedisTemplate redis, AnalyticsProperties analyticsProperties) {
        this(new AnalyticsRedisAggregateWriter(redis, analyticsProperties), analyticsProperties);
    }

    @Autowired
    public RedisAnalyticsVisitEventAppender(
            AnalyticsRedisAggregateWriter aggregateWriter,
            AnalyticsProperties analyticsProperties
    ) {
        this.aggregateWriter = aggregateWriter;
        this.analyticsProperties = analyticsProperties;
    }

    /**
     * 写入单次访问的规范化事件字段。
     *
     * <p>{@code requestId} 在这里生成，供原子聚合去重。原始 IP 不进入 Redis；只保存带盐访客指纹。
     * 维度与原始明细不再进入基础统计路径。</p>
     *
     * @param event 由重定向链路提供的访问上下文；为空时无副作用
     */
    @Override
    public void append(AnalyticsVisitEventService.RedirectVisitEvent event) {
        if (aggregateWriter == null || event == null) {
            return;
        }

        long occurredAtMillis = event.occurredAtMillis() > 0 ? event.occurredAtMillis() : System.currentTimeMillis();
        LocalDate day = Instant.ofEpochMilli(occurredAtMillis).atOffset(ZoneOffset.UTC).toLocalDate();
        String visitor = VisitorFingerprint.fingerprint(
                day,
                new VisitContext(event.ip(), event.userAgent()),
                analyticsProperties == null ? null : analyticsProperties.getSalt()
        );
        aggregateWriter.write(
                event.tenantId(),
                event.linkId(),
                occurredAtMillis,
                event.applicationId(),
                event.domainId(),
                visitor,
                UUID.randomUUID().toString().replace("-", "")
        );
    }
}
