package com.linkforge.contract.governance;

/**
 * 跨上下文返回的审批请求安全视图。
 *
 * <p>本视图刻意不包含 {@code beforeSnapshot}/{@code afterSnapshot}，因为它们按操作类型解释，可能是版本化 JSON、
 * 历史自由文本或 {@code null}，并可能暴露敏感资源状态。需要执行的上下文只会收到
 * {@link ApprovalExecutionRequest}；普通提交方不应依赖本类型恢复或修改审批 payload。</p>
 *
 * <p>{@code operation} 和 {@code status} 是当前 Governance 实现由内部枚举 {@code name()} 产生的字符串，分别
 * 不是 JSON payload 的 {@code type}，也不是独立的 HTTP 枚举版本协议。状态语义为：
 * {@code PENDING_APPROVAL} 尚未决定；{@code APPROVED} 表示已批准但没有自动执行器；
 * {@code EXECUTED} 表示已批准且执行器在当前批准流程中成功返回。申请人和审批人字段都是历史身份快照。</p>
 *
 * @param id 服务端分配的审批请求 ID
 * @param tenantId 审批所属租户；调用方只能在该租户作用域内使用本视图
 * @param operation 当前操作枚举名称，例如 {@code PUBLIC_LINK_DESTINATION_CHANGE}
 * @param targetApplicationId 可空的目标应用范围；其可空语义由操作类型定义
 * @param requestedByUserId 提交审批时记录的申请人用户 ID
 * @param requestedByEmail 提交审批时记录的申请人邮箱快照
 * @param status 当前审批状态枚举名称
 * @param approverUserId 审批人用户 ID；待审批时为 {@code null}
 * @param approverEmail 审批人邮箱快照；待审批时为 {@code null}
 * @param decisionReason 可空的审批理由；即使已批准也允许为空
 */
public record ApprovalRequestView(
        long id,
        long tenantId,
        String operation,
        Long targetApplicationId,
        long requestedByUserId,
        String requestedByEmail,
        String status,
        Long approverUserId,
        String approverEmail,
        String decisionReason
) {
}
