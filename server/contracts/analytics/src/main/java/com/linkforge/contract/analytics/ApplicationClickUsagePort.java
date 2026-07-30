package com.linkforge.contract.analytics;

import java.time.LocalDate;

/**
 * 查询应用在 UTC 半开日期区间内已持久化点击量的兼容契约。
 *
 * <p>标准实现累加 MySQL 日表的 PV，不包含尚未从 Redis Stream flush 的访问，也不进行原子预留；它只能用于
 * 额度计数器首次初始化或兼容查询，不能代替 {@link ApplicationClickQuotaReservationPort} 做并发硬额度判断。</p>
 */
public interface ApplicationClickUsagePort {

    /**
     * 返回 {@code [fromInclusiveUtc,toExclusiveUtc)} 内已落库的应用链接 PV 总和。
     *
     * <p>标准 MyBatis adapter 对非正 ID、null 日期或非递增区间返回 {@code 0}；调用方不能把该值解释为
     * 流中没有待处理访问或额度仍可预留。</p>
     *
     * @param tenantId 目标租户的正 ID
     * @param applicationId 目标应用的正 ID；必须属于 tenantId，归属校验不由此只读端口替代
     * @param fromInclusiveUtc UTC 起始日期（包含）
     * @param toExclusiveUtc UTC 结束日期（不包含），必须晚于 fromInclusiveUtc
     * @return 已落库日表 PV 的非负和；无效输入在标准实现中返回 {@code 0}
     * @throws RuntimeException 当有效查询的底层存储失败时，标准实现不吞掉该异常
     */
    long countApplicationClicks(
            long tenantId,
            long applicationId,
            LocalDate fromInclusiveUtc,
            LocalDate toExclusiveUtc
    );
}
