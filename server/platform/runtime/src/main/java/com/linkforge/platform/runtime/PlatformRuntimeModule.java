package com.linkforge.platform.runtime;

import com.linkforge.platform.application.PlatformApplicationConfig;
import com.linkforge.platform.infrastructure.PlatformInfrastructureConfig;
import com.linkforge.platform.interfaces.PlatformInterfacesConfig;
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
