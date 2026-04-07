package com.linkforge.platform.interfaces;

import com.linkforge.platform.application.PlatformApplicationConfig;
import com.linkforge.platform.infrastructure.PlatformInfrastructureConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import({
        PlatformApplicationConfig.class,
        PlatformInfrastructureConfig.class,
        PlatformInterfacesConfig.class
})
public class PlatformRuntimeModule {
}
