package com.linkforge.contract.analytics;

/**
 * Redirect 向 Analytics 发布的单次真实跳转访问。
 *
 * <p>它表示服务器已通过跳转可用性与额度决策的记录请求，不证明浏览器已经到达目标 URL。preview、not-found
 * 和 quota 拒绝响应不应创建该记录。{@code occurredAtMillis} 应为真实事件的正 Unix epoch milliseconds；
 * 标准 Redis appender 会把非正值替换为写入时刻，故补录必须传入真实的正值以保留统计日。</p>
 *
 * <p>applicationId/domainId 对 legacy 链接可为空，visitContext 也可为空。{@code originalUrl} 是跨模块兼容
 * 快照，标准 Redis Stream 不写入它；其他实现若要持久化或记录该值，必须先经过隐私评审。</p>
 *
 * @param tenantId 所属租户的正 ID；本 record 不校验，坏值会在标准聚合链路被丢弃
 * @param linkId 已允许跳转的短链正 ID；它与 tenantId 共同构成统计隔离范围
 * @param occurredAtMillis 事件发生时刻的 UTC Unix epoch milliseconds；正值保留原始统计日，非正值由标准 appender
 *                         改为写入时刻
 * @param applicationId 所属应用的正 ID；legacy 链接可为 {@code null}
 * @param domainId 所属域名的正 ID；legacy 链接可为 {@code null}
 * @param code 跳转时的短码；标准 appender 对 {@code null} 或空白值不写入 Stream 字段
 * @param originalUrl 可选的目标 URL 兼容快照；标准 Redis Stream 不写入该字段
 * @param visitContext 来自不可信 HTTP 请求的最小访问上下文；允许为 {@code null}，表示没有可采集维度
 */
public record RedirectVisitRecord(
        long tenantId,
        long linkId,
        long occurredAtMillis,
        Long applicationId,
        Long domainId,
        String code,
        String originalUrl,
        VisitContext visitContext
) {
}
