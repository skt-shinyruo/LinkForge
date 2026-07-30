package com.linkforge.governance.domain;

/**
 * 审批请求生命周期状态。
 *
 * <p>枚举名称会持久化到数据库并通过查询契约对外暴露，属于稳定值。当前聚合和 HTTP 接口只实现
 * {@code PENDING_APPROVAL -> APPROVED -> EXECUTED}；{@code REJECTED}/{@code CANCELLED}/{@code EXPIRED}
 * 目前是保留状态，尚无对应转换命令，不应绕过领域行为直接构造为新的业务状态。</p>
 */
public enum ApprovalStatus {
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    CANCELLED,
    EXECUTED,
    EXPIRED
}
