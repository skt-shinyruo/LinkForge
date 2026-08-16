package com.linkforge.shortlink.application.migration;

/**
 * 单条短链 ownership reconciliation 的稳定结果。
 *
 * <p>结果区分已收敛、需要重试的 CAS 竞争和不会被相同目标修复的终态，供在线调用与历史任务共享。
 * {@code version} 是本次观察到或成功写入的聚合版本；未找到短链时为 {@code -1}。</p>
 */
public record ShortLinkOwnershipReconciliationResult(long linkId, Status status, long version) {

    public enum Status {
        RECONCILED,
        ALREADY_RECONCILED,
        RETRYABLE_CONFLICT,
        OWNERSHIP_CONFLICT,
        NOT_FOUND
    }
}
