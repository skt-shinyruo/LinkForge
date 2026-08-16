package com.linkforge.shortlink.application.port;

import com.linkforge.shortlink.application.migration.LegacyShortLinkBackfillProgress;

import java.util.List;

/**
 * 有界 legacy ownership reconciliation 的 durable 发现与进度存储。
 *
 * <p>{@link #takeBatch(long, long, long, int)} 必须锁定租户 checkpoint，以稳定 keyset cursor 发现未跟踪的
 * legacy ID，持久化 work item，并在一个事务内返回最多 {@code limit} 个可执行项。reconciliation 本身刻意
 * 留在此端口之外，调用方必须走公开应用用例。</p>
 */
public interface LegacyShortLinkBackfillStore {

    enum Outcome {
        RECONCILED,
        ALREADY_RECONCILED,
        RETRYABLE,
        PERMANENT_FAILURE,
        NOT_FOUND
    }

    record WorkItem(long tenantId, long linkId, long applicationId, long domainId) {
    }

    List<WorkItem> takeBatch(long tenantId, long applicationId, long domainId, int limit);

    /** 持久化一次尝试结果；并发晚到结果不得把终态降级。 */
    void recordOutcome(long tenantId, long linkId, Outcome outcome, String detail);

    LegacyShortLinkBackfillProgress progress(long tenantId);
}
