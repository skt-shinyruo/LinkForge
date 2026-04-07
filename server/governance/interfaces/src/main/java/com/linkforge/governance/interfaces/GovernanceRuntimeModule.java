package com.linkforge.governance.interfaces;

import com.linkforge.governance.application.GovernanceApplicationConfig;
import com.linkforge.governance.infrastructure.GovernanceInfrastructureConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import({
        GovernanceApplicationConfig.class,
        GovernanceInfrastructureConfig.class,
        GovernanceInterfacesConfig.class
})
public class GovernanceRuntimeModule {
}
