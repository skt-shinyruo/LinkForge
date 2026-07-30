package com.linkforge.platform.domain;

/**
 * 判定某个应用是否可以使用指定域名的纯领域策略。
 *
 * <p>调用方必须先按租户范围加载应用和域名，并为共享域名查询授权关系；本策略不访问仓储，也不负责
 * 租户隔离。判定顺序固定为“域名已启用”优先，然后根据范围检查专属绑定或共享授权，从而向应用层
 * 提供稳定、可映射为业务错误的失败原因。</p>
 */
public class DomainAuthorizationPolicy {

    /**
     * 要求应用具备域名使用权。
     *
     * <p>专属域名只比较 {@link Domain#applicationId()}，忽略
     * {@code sharedDomainAuthorized}；共享域名则要求该标志为 {@code true}。任何情况下，非
     * {@link DomainStatus#ACTIVE} 域名都会首先被拒绝。</p>
     *
     * @param applicationId 待授权的应用 ID
     * @param domain 已在正确租户范围内加载的域名
     * @param sharedDomainAuthorized 共享域名授权关系是否存在
     * @throws DomainAuthorizationException 域名未启用、专属绑定不匹配或共享授权不存在
     */
    public void requireApplicationCanUseDomain(
            long applicationId,
            Domain domain,
            boolean sharedDomainAuthorized
    ) {
        if (domain.status() != DomainStatus.ACTIVE) {
            throw new DomainAuthorizationException(DomainAuthorizationException.Reason.DOMAIN_NOT_ACTIVE);
        }

        if (domain.scope() == DomainScope.APPLICATION_DEDICATED) {
            if (domain.applicationId() == null || domain.applicationId() != applicationId) {
                throw new DomainAuthorizationException(
                        DomainAuthorizationException.Reason.DEDICATED_DOMAIN_MISMATCH
                );
            }
            return;
        }

        if (!sharedDomainAuthorized) {
            throw new DomainAuthorizationException(
                    DomainAuthorizationException.Reason.SHARED_DOMAIN_NOT_AUTHORIZED
            );
        }
    }
}
