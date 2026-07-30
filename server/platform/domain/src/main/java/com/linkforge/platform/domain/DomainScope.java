package com.linkforge.platform.domain;

/**
 * 域名可被应用使用的范围；该值决定授权策略采用专属绑定还是共享授权关系。
 */
public enum DomainScope {
    /** 租户内共享，但每个应用仍需显式授权。 */
    TENANT_SHARED,
    /** 仅绑定的单个应用可用，不读取共享授权关系。 */
    APPLICATION_DEDICATED
}
