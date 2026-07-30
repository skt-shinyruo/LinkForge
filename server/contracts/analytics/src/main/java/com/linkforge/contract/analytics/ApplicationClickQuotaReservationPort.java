package com.linkforge.contract.analytics;

import java.time.LocalDate;

/**
 * Redirect 在真实跳转前尝试预留应用月点击额度的发布契约。
 *
 * <p>它没有 requestId 或撤销操作，单次调用不是幂等命令：调用超时后即使调用方不知道 Redis 是否已计数，
 * 也不能盲目重试，否则可能占用多个名额。</p>
 */
public interface ApplicationClickQuotaReservationPort {

    /**
     * 尝试为 UTC 半开月区间 {@code [fromInclusiveUtc,toExclusiveUtc)} 预留一次点击。
     *
     * <p>调用方应传入正的 tenant/application ID、UTC 月初与下月月初；{@code monthlyClickLimit <= 0}
     * 表示不限额。当前 Redis 实现只有脚本明确判定额度耗尽时返回 {@code false}；无上限、无效输入、Redis/
     * MySQL 基线查询或脚本异常均 fail-open 返回 {@code true}。因此 {@code true} 只表示允许 Redirect 继续，
     * 不保证计数器已经成功递增，也不能作为精确计费依据。</p>
     *
     * @param tenantId 目标租户的正 ID；无效值在标准实现中 fail-open
     * @param applicationId 目标应用的正 ID；必须属于 tenantId，归属校验由调用方完成
     * @param fromInclusiveUtc UTC 自然月首日；与 toExclusiveUtc 组成半开窗口，{@code null} 或非递增窗口在
     *                         标准实现中 fail-open
     * @param toExclusiveUtc 下一 UTC 自然月首日（不包含）；本端口不根据 JVM 本地时区解释日期
     * @param monthlyClickLimit 月点击上限；小于等于零表示不限制且不会要求实际预留
     * @return {@code false} 表示实现明确拒绝本次额度；{@code true} 表示允许继续跳转
     */
    boolean tryReserveMonthlyClick(
            long tenantId,
            long applicationId,
            LocalDate fromInclusiveUtc,
            LocalDate toExclusiveUtc,
            long monthlyClickLimit
    );
}
