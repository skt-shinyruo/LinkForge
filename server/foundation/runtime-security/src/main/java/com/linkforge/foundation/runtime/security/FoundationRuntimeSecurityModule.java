package com.linkforge.foundation.runtime.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Foundation 的安全上下文运行时导出模块。
 *
 * <p>仅注册 HTTP 适配层使用的 actor/tenant helper；JWT 和 API Key 的实际鉴权实现由所属上下文提供。</p>
 */
@Configuration(proxyBeanMethods = false)
@Import({
        TenantGuard.class,
        PrincipalActorMapper.class
})
public class FoundationRuntimeSecurityModule {
}
