package com.linkforge.shortlink.runtime;

import com.linkforge.shortlink.application.ShortlinkApplicationConfig;
import com.linkforge.shortlink.infrastructure.ShortlinkInfrastructureConfig;
import com.linkforge.shortlink.interfaces.ShortlinkInterfacesConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import({
        ShortlinkApplicationConfig.class,
        ShortlinkInfrastructureConfig.class,
        ShortlinkInterfacesConfig.class
})
public class ShortlinkRuntimeModule {
}
