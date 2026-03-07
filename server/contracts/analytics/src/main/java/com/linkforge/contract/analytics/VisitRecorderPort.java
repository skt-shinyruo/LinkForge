package com.linkforge.contract.analytics;

/**
 * Outbound Port: 由 redirect/edge 侧调用，向 analytics 上报访问事件（PV/UV/维度/明细）。
 *
 * <p>说明：实现侧必须遵循“写统计不影响主链路”的降级原则。</p>
 */
public interface VisitRecorderPort {

    void recordVisit(long tenantId, long linkId, VisitContext visitContext);
}

