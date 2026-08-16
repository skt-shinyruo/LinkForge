package com.linkforge.analytics.application;

import com.linkforge.analytics.application.port.AnalyticsVisitEventAppender;
import com.linkforge.contract.analytics.RedirectVisitRecord;
import com.linkforge.contract.analytics.VisitContext;
import com.linkforge.contract.analytics.VisitRecorderPort;
import com.linkforge.foundation.config.AnalyticsProperties;
import com.linkforge.foundation.observability.OperationalMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

/**
 * Redirect 到 Analytics 基础访问流的应用适配器。
 *
 * <p>只应由 Redirect 在真实跳转已经确认后调用；preview、未命中、不可用和配额拒绝不应构造记录。该服务把稳定的
 * contracts 记录转换为 Analytics 内部事件，具体的 Redis Stream 写入、维度归一化与 PV/UV 投影由下游实现负责。</p>
 *
 * <p>{@code app.analytics.events.fail-open=true}（以及未注入配置的兼容构造路径）会吞掉 appender 运行时异常以保护跳转；
 * 这表示本次统计可能丢失，不能解释为已持久化。设为 {@code false} 时异常原样上抛，调用方据此决定跳转失败语义。</p>
 */
@Service
public class AnalyticsVisitEventService implements VisitRecorderPort {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsVisitEventService.class);

    private final AnalyticsVisitEventAppender appender;
    private final AnalyticsProperties analyticsProperties;
    private final OperationalMetrics metrics;

    public AnalyticsVisitEventService(AnalyticsVisitEventAppender appender) {
        this(appender, null, OperationalMetrics.noop());
    }

    public AnalyticsVisitEventService(AnalyticsVisitEventAppender appender, AnalyticsProperties analyticsProperties) {
        this(appender, analyticsProperties, OperationalMetrics.noop());
    }

    @Autowired
    public AnalyticsVisitEventService(
            AnalyticsVisitEventAppender appender,
            AnalyticsProperties analyticsProperties,
            OperationalMetrics metrics
    ) {
        this.appender = appender;
        this.analyticsProperties = analyticsProperties;
        this.metrics = metrics == null ? OperationalMetrics.noop() : metrics;
    }

    /**
     * 将跨上下文的真实跳转记录转换为内部事件。
     *
     * <p>空记录是无操作。访问上下文允许为空，此时客户端字段保持 {@code null}，由下游决定归一化和缺失值语义。</p>
     */
    @Override
    public void recordVisit(RedirectVisitRecord visit) {
        if (visit == null) {
            return;
        }
        VisitContext context = visit.visitContext();
        append(new RedirectVisitEvent(
                visit.tenantId(),
                visit.linkId(),
                visit.occurredAtMillis(),
                visit.applicationId(),
                visit.domainId(),
                visit.code(),
                visit.originalUrl(),
                context == null ? null : context.ip(),
                context == null ? null : context.userAgent(),
                context == null ? null : context.referer(),
                context == null ? null : context.acceptLanguage(),
                context == null ? null : context.trackingParams()
        ));
    }

    /**
     * 追加一条基础访问事件。
     *
     * <p>空事件或未配置 appender 是无操作。appender 成功返回只代表其已接受事件，异步消费、聚合和落库仍可能延迟或
     * 重放，故整个链路不提供 exactly-once 保证。</p>
     *
     * @param event 要写入的内部访问事件
     * @throws RuntimeException 当 fail-open 关闭且 appender 失败时向上传播
     */
    public void append(RedirectVisitEvent event) {
        if (event == null || appender == null) {
            return;
        }
        try {
            appender.append(event);
            metrics.increment("linkforge.analytics.visit.append", "result", "success");
        } catch (RuntimeException e) {
            String reason = stableFailureReason(e);
            if (!isFailOpen()) {
                metrics.increment(
                        "linkforge.analytics.degraded",
                        "component", "visit_appender",
                        "reason", reason
                );
                throw e;
            }
            metrics.increment(
                    "linkforge.analytics.fail_open",
                    "component", "visit_appender",
                    "reason", reason
            );
            log.debug(
                    "append analytics visit event failed: tenantId={}, linkId={}, code={}, err={}",
                    event.tenantId(),
                    event.linkId(),
                    event.code(),
                    e.getMessage()
            );
        }
    }

    private boolean isFailOpen() {
        AnalyticsProperties.Events cfg = analyticsProperties == null ? null : analyticsProperties.getEvents();
        return cfg == null || cfg.isFailOpen();
    }

    private static String stableFailureReason(Throwable failure) {
        Throwable current = failure;
        boolean serialization = false;
        boolean redis = false;
        for (int depth = 0; current != null && depth < 16; depth++) {
            String type = current.getClass().getName().toLowerCase(Locale.ROOT);
            String message = current.getMessage() == null
                    ? ""
                    : current.getMessage().toLowerCase(Locale.ROOT);
            if (type.contains("capacity") || message.contains("maxmemory") || message.contains("oom command")) {
                return "capacity";
            }
            if (type.contains("serializ") || type.contains("codec") || type.contains("json")) {
                serialization = true;
            }
            if (type.contains("redis") || type.contains("connection") || type.contains("timeout")) {
                redis = true;
            }
            current = current.getCause();
        }
        if (serialization) {
            return "serialization";
        }
        if (redis) {
            return "redis";
        }
        return "appender";
    }

    /**
     * Redirect 访问事件的内部稳定载荷。
     *
     * <p>{@code occurredAtMillis} 是 UTC epoch milliseconds；applicationId/domainId 可以为空以兼容 legacy 链接。
     * trackingParams 在构造时复制为空安全、不可变快照，后续调用方修改原 Map 不会改变已排队事件。</p>
     */
    public record RedirectVisitEvent(
            long tenantId,
            long linkId,
            long occurredAtMillis,
            Long applicationId,
            Long domainId,
            String code,
            String originalUrl,
            String ip,
            String userAgent,
            String referer,
            String acceptLanguage,
            Map<String, String> trackingParams
    ) {

        public RedirectVisitEvent {
            trackingParams = trackingParams == null ? Map.of() : Map.copyOf(trackingParams);
        }
    }
}
