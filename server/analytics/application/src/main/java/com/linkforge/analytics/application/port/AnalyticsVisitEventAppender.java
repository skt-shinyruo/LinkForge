package com.linkforge.analytics.application.port;

import com.linkforge.analytics.application.AnalyticsVisitEventService;

/**
 * Analytics 基础访问事件的异步接收端口。
 *
 * <p>实现通常把事件写入 Redis Stream；成功返回仅表示接收端接受了事件，不承诺投影、持久化或 exactly-once。
 * 运行时异常交由 {@link AnalyticsVisitEventService} 依据 {@code app.analytics.events.fail-open} 决定吞掉还是传播，
 * 因此实现不得自行伪造成功。</p>
 */
public interface AnalyticsVisitEventAppender {

    /**
     * 接收一条不可变的访问事件快照。
     *
     * @param event 已由应用服务复制 trackingParams 的事件
     * @throws RuntimeException 基础设施暂时不可用或拒绝事件时抛出
     */
    void append(AnalyticsVisitEventService.RedirectVisitEvent event);
}
