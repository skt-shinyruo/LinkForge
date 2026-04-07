package com.linkforge.app.compose;

import com.linkforge.redirect.application.RedirectApplicationConfig;
import com.linkforge.redirect.infrastructure.RedirectInfrastructureConfig;
import com.linkforge.redirect.interfaces.RedirectInterfacesConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import({
        RedirectApplicationConfig.class,
        RedirectInfrastructureConfig.class,
        RedirectInterfacesConfig.class
})
public class RedirectModule {
}
