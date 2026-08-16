package com.linkforge.shortlink.application.migration;

/** 一次有界 legacy ownership reconciliation 调用的结果。 */
public record LegacyShortLinkBackfillBatchResult(
        long tenantId,
        long applicationId,
        long domainId,
        int attemptedCount,
        int reconciledCount,
        int alreadyReconciledCount,
        int retryableCount,
        int permanentFailureCount,
        int notFoundCount,
        LegacyShortLinkBackfillProgress progress
) {
}
