package com.linkforge.contract.governance;

/**
 * Governance 与审批执行器之间共享的稳定敏感操作类别。
 *
 * <p>此枚举用于执行器选择和领域操作映射，不等同于 {@link ApprovalPayloadTypes} 的 JSON {@code type} token。
 * 为枚举新增值时，必须同步更新领域映射、审批矩阵、执行器注册策略、payload/历史兼容规则及文档；否则批准流程会
 * 在找不到执行器时仅停留在 {@code APPROVED}，或在映射阶段失败。</p>
 */
public enum SensitiveOperation {
    /** 应用级公开短链目标地址变更；当前 Shortlink 执行器要求版本化 before/after JSON 快照。 */
    PUBLIC_LINK_DESTINATION_CHANGE
}
