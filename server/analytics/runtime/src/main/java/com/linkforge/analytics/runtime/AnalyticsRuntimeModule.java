package com.linkforge.analytics.runtime;

import com.linkforge.analytics.application.AnalyticsApplicationConfig;
import com.linkforge.analytics.infrastructure.AnalyticsInfrastructureConfig;
import com.linkforge.analytics.interfaces.AnalyticsInterfacesConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import({
        AnalyticsApplicationConfig.class,
        AnalyticsInfrastructureConfig.class,
        AnalyticsInterfacesConfig.class
})
public class AnalyticsRuntimeModule {
}
