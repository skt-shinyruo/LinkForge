package com.linkforge.platform.domain;

/**
 * 域名是否允许通过应用使用权校验的状态。
 *
 * <p>{@link DomainAuthorizationPolicy} 对非 {@link #ACTIVE} 域名一律拒绝；状态不会因为已有
 * 应用授权关系而被绕过。</p>
 */
public enum DomainStatus {
    /** 域名可以继续进入作用域和授权关系检查。 */
    ACTIVE,
    /** 域名不能通过新的使用权校验；已有授权关系仍保留但在该校验中不生效。 */
    DISABLED
}
