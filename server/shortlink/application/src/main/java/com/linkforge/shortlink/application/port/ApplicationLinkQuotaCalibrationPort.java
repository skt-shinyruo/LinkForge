package com.linkforge.shortlink.application.port;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 将一条刚纳入应用 scope 的既有短链计入月度发链用量。
 *
 * <p>调用方必须先用短链聚合的乐观锁成功完成 ownership reconciliation，再在同一事务内调用本端口。
 * 实现需要与新建短链的额度预留使用同一串行化边界：若月份基线尚不存在，基线统计已经包含刚完成
 * reconciliation 的短链；若基线已存在，则只递增一次。任何锁获取或持久化失败都必须抛出异常，使
 * ownership、用量、事件和缓存失效意图一起回滚。</p>
 */
public interface ApplicationLinkQuotaCalibrationPort {

    /**
     * 把一条既有短链计入指定应用的 UTC 月度用量。
     *
     * @param tenantId 短链租户
     * @param applicationId 目标应用
     * @param monthStartUtc UTC 月初
     * @param fromInclusiveUtc 月份开始（含）
     * @param toExclusiveUtc 下月开始（不含）
     */
    void includeReconciledLink(
            long tenantId,
            long applicationId,
            LocalDate monthStartUtc,
            LocalDateTime fromInclusiveUtc,
            LocalDateTime toExclusiveUtc
    );
}
