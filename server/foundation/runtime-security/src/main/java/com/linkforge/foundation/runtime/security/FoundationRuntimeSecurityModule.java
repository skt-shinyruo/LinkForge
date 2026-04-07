package com.linkforge.foundation.runtime.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import({
        TenantGuard.class,
        PrincipalActorMapper.class
})
public class FoundationRuntimeSecurityModule {
}
