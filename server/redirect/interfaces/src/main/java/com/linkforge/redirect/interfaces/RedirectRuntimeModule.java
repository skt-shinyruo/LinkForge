package com.linkforge.redirect.interfaces;

import com.linkforge.redirect.application.RedirectApplicationConfig;
import com.linkforge.redirect.infrastructure.RedirectInfrastructureConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import({
        RedirectApplicationConfig.class,
        RedirectInfrastructureConfig.class,
        RedirectInterfacesConfig.class
})
public class RedirectRuntimeModule {
}
