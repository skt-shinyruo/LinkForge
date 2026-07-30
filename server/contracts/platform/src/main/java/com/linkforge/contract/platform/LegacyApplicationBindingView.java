package com.linkforge.contract.platform;

/**
 * 旧未分应用短链迁移后使用的默认应用和域名绑定。
 *
 * <p>两个 ID 必须属于同一租户，并表示该租户迁移兼容路径使用的默认 ACTIVE application 与其可用 domain。
 * 该 view 不是任意应用/域名配对的授权凭据；新建应用级短链仍须经过 {@link ApplicationScopePort} 校验。</p>
 *
 * @param applicationId 默认应用 ID，必须大于 {@code 0}
 * @param domainId 与默认应用关联的默认域名 ID，必须大于 {@code 0}
 */
public record LegacyApplicationBindingView(
        long applicationId,
        long domainId
) {
}
