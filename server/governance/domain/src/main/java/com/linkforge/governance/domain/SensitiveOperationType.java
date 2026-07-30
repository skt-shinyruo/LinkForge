package com.linkforge.governance.domain;

/**
 * Governance 上下文识别的敏感操作类型。
 *
 * <p>枚举名称既用于持久化，也通过应用层显式映射到发布契约的 {@code SensitiveOperation}；新增值时必须同步
 * 更新映射、审批权限矩阵、payload 类型/版本定义和执行器覆盖，不能依赖两个枚举的序号或隐式名称转换。</p>
 */
public enum SensitiveOperationType {
    APPLICATION_QUOTA_INCREASE,
    EXTERNAL_DOMAIN_BINDING,
    PUBLIC_LINK_DESTINATION_CHANGE,
    ANALYTICS_DETAIL_EXPORT
}
