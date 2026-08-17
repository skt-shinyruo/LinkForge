package com.linkforge.contract.analytics;

/**
 * Redirect 调用的基础 PV/UV 记录端口。
 *
 * <p>默认部署为 fail-open，但实现可以通过 {@code app.analytics.events.fail-open=false}
 * 选择传播异常。当前标准实现每次调用生成新的 requestId，接口本身没有调用方幂等键，因此重复调用会重复增加
 * PV。正常返回表示 Redis 聚合已完成，但 MySQL flush 仍为异步；fail-open 返回不证明统计成功。</p>
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
