package com.linkforge.analytics.application.port;

import com.linkforge.analytics.application.AnalyticsVisitEventService;

/**
 * Analytics 基础访问事件接收端口。
 *
 * <p>标准实现同步更新 Redis 聚合；成功返回不承诺异步 MySQL flush 已完成或跨调用 exactly-once。
 * 运行时异常交由 {@link AnalyticsVisitEventService} 依据 {@code app.analytics.events.fail-open} 决定吞掉还是传播，
 * 因此实现不得自行伪造成功。</p>
 */
public interface AnalyticsVisitEventAppender {

    /**
     * 接收一条不可变的访问事件快照。
     *
     * @param event 访问事件
     * @throws RuntimeException 基础设施暂时不可用或拒绝事件时抛出
     */
    void append(AnalyticsVisitEventService.RedirectVisitEvent event);
}
