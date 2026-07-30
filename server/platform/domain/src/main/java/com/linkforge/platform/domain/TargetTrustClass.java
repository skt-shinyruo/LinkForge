package com.linkforge.platform.domain;

/**
 * 域名目标的信任分类元数据。
 *
 * <p>当前实现只将该分类随域名快照持久化，授权策略并不读取它。该值本身不授予域名使用权，
 * 也不替代租户、状态和 {@link DomainScope} 校验。</p>
 */
public enum TargetTrustClass {
    /** 由平台或租户直接控制的第一方目标。 */
    FIRST_PARTY,
    /** 由外部主体控制的第三方目标。 */
    THIRD_PARTY,
    /** 仅供受控内部链路使用的目标。 */
    INTERNAL
}
