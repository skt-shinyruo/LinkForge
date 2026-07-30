package com.linkforge.contract.analytics;

/**
 * Redirect 调用的访问记录发布端口，用于基础 PV/UV、维度和可选明细。
 *
 * <p>默认部署为 fail-open，但实现可以通过 {@code app.analytics.events.fail-open=false}
 * 选择传播异常。当前标准实现为每次调用生成新的访问事件/明细 requestId，接口没有幂等键；重复调用、上游
 * 重放或 consumer reclaim 可能重复增加 PV。成功返回只表示同步 appender 未报告失败，不表示 Redis Stream、
 * 聚合或 MySQL 已完成持久化；fail-open 返回更不能作为事件已入流的证据。</p>
 */
public interface VisitRecorderPort {

    /**
     * 记录一次服务端已经允许的真实跳转。
     *
     * <p>标准服务把 null 记录作为无操作；调用方不应以 null 表示失败或取消。实现可按 fail-open 配置吞掉
     * 基础设施异常，或向 Redirect 主链路传播异常。</p>
     *
     * @param visit 已允许的跳转记录；允许为 {@code null}，标准实现把它视为无操作
     * @throws RuntimeException 当实现配置为 fail-closed 且事件追加失败时向调用方传播；该异常不表示跳转本身
     *                          已被撤销
     */
    void recordVisit(RedirectVisitRecord visit);
}
