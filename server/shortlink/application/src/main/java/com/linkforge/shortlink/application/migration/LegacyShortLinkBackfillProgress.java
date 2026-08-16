package com.linkforge.shortlink.application.migration;

/** 单个租户 legacy ownership reconciliation 的 durable 进度与结果计数。 */
public record LegacyShortLinkBackfillProgress(
        long lastScannedLinkId,
        boolean scanExhausted,
        long pendingCount,
        long retryableCount,
        long permanentFailureCount,
        long reconciledCount,
        long alreadyReconciledCount,
        long notFoundCount
) {

    /** 当前扫描已无未发现的 key，也没有仍可自动重试的 work item。 */
    public boolean converged() {
        return scanExhausted && pendingCount == 0 && retryableCount == 0;
    }
}
