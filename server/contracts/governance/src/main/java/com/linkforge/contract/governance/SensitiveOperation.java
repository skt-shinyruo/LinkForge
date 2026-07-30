package com.linkforge.contract.governance;

/**
 * Governance 与审批执行器之间共享的稳定敏感操作类别。
 *
 * <p>此枚举用于执行器选择和领域操作映射，不等同于 {@link ApprovalPayloadTypes} 的 JSON {@code type} token。
 * 为枚举新增值时，必须同步更新领域映射、审批矩阵、执行器注册策略、payload/历史兼容规则及文档；否则批准流程会
 * 在找不到执行器时仅停留在 {@code APPROVED}，或在映射阶段失败。</p>
 */
public enum SensitiveOperation {
    /** 应用月短链/点击额度提升；结构化审批 payload 使用 {@code applicationQuotaIncrease/v1}。 */
    APPLICATION_QUOTA_INCREASE,

    /** 外部域名绑定；历史审批快照可能是自由文本，执行器不得假定其一定是当前 JSON payload。 */
    EXTERNAL_DOMAIN_BINDING,

    /** 应用级公开短链目标地址变更；当前 Shortlink 执行器要求版本化 before/after JSON 快照。 */
    PUBLIC_LINK_DESTINATION_CHANGE,

    /** 访问明细导出审批；当前 payload 的 before snapshot 可以为 {@code null}。 */
    ANALYTICS_DETAIL_EXPORT
}
